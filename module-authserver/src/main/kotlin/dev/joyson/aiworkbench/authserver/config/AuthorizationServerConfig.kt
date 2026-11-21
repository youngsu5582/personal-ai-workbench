package dev.joyson.aiworkbench.authserver.config

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import dev.joyson.aiworkbench.authserver.AuthorizationServerPaths
import dev.joyson.aiworkbench.authserver.AuthserverProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2Token
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationServerMetadata
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationServerMetadataClaimNames
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.AnyRequestMatcher

/**
 * OAuth 2.1 인가 서버. 메타데이터 · JWKS · 인가 · 토큰 엔드포인트를 이 체인이 받는다.
 *
 * 순서가 `auth` 모듈의 체인들보다 앞이어야 한다. 그쪽의 로그인 체인은 남은 경로를 모두 받으므로,
 * 이 체인이 뒤에 서면 인가 서버의 엔드포인트가 로그인 체인으로 간다.
 * 받는 경로는 인가 서버의 엔드포인트뿐이라 `/api` 와도, Google 로그인 경로와도 겹치지 않는다.
 *
 * 발급자는 `spring.security.oauth2.authorizationserver.issuer` 가 정하고, 그 값은 `workbench.auth.issuer` 를 가리킨다.
 */
@Configuration
class AuthorizationServerConfig {

    @Bean
    @Order(0)
    fun authorizationServerSecurityFilterChain(
        http: HttpSecurity,
        signingKey: SigningKey,
        properties: AuthserverProperties,
        settings: AuthorizationServerSettings,
    ): SecurityFilterChain {
        check(settings.authorizationEndpoint == AuthorizationServerPaths.AUTHORIZATION) {
            "인가 엔드포인트가 ${settings.authorizationEndpoint} 인데 다른 모듈에는 ${AuthorizationServerPaths.AUTHORIZATION} 로 알리고 있다"
        }
        http
            .oauth2AuthorizationServer { server ->
                http.securityMatcher(server.endpointsMatcher)
                server.tokenGenerator(tokenGenerator(signingKey, properties))
                server.authorizationServerMetadataEndpoint { endpoint ->
                    endpoint.authorizationServerMetadataCustomizer { metadata -> describeOnlyWhatIsOffered(metadata) }
                }
            }
            .authorizeHttpRequests {
                // 토큰 조회는 쓰지 않는다. API 는 JWKS 로 토큰을 직접 검증한다. 라이브러리에 끄는 설정이 없어 여기서 막는다.
                it.requestMatchers(settings.tokenIntrospectionEndpoint).denyAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                // 사용자를 확인하는 일은 Google 에 맡긴다. 로그인하지 않은 채 인가 엔드포인트에 오면 Google 로그인으로 보낸다.
                it.defaultAuthenticationEntryPointFor(
                    LoginUrlAuthenticationEntryPoint(GOOGLE_LOGIN_PATH),
                    PathPatternRequestMatcher.withDefaults().matcher(AuthorizationServerPaths.AUTHORIZATION),
                )
                // 나머지 엔드포인트는 사람이 아니라 프로그램이 부른다. 로그인 화면으로 보내지 않고 401 로 답한다.
                it.defaultAuthenticationEntryPointFor(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), AnyRequestMatcher.INSTANCE)
            }
        return http.build()
    }

    @Bean
    fun signingKey(properties: AuthserverProperties): SigningKey = SigningKey(properties.signingKey)

    /** JWKS 엔드포인트는 이 키의 공개 부분만 내보낸다. */
    @Bean
    fun jwkSource(signingKey: SigningKey): JWKSource<SecurityContext> = ImmutableJWKSet(JWKSet(signingKey.jwk))

    /**
     * 등록된 클라이언트는 콘솔 백엔드 하나다.
     *
     * 비밀값으로 인증하는 기밀 클라이언트지만 PKCE 도 요구한다. OAuth 2.1 은 인가 코드 흐름을 쓰는 모든 클라이언트에 PKCE 를 요구한다.
     * PKCE 검증은 라이브러리가 하고, 아래 `requireProofKey` 가 그 검증을 켜는 스위치다.
     */
    @Bean
    fun registeredClientRepository(properties: AuthserverProperties): RegisteredClientRepository {
        // 비밀값을 그대로 들고 있지 않고 해시로 바꿔 둔다. 토큰 엔드포인트가 받은 값과 이 해시를 비교한다.
        val secretHash = checkNotNull(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(properties.console.clientSecret))
        val console = RegisteredClient.withId(CONSOLE_CLIENT_ID)
            .clientId(CONSOLE_CLIENT_ID)
            .clientSecret(secretHash)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(properties.console.redirectUri)
            .clientSettings(
                ClientSettings.builder()
                    // PKCE. 인가 요청에 code_challenge(난수의 해시)가 없으면 거절하고, 토큰 교환 때 받은 code_verifier(난수 원문)의
                    // 해시가 맞는지 라이브러리가 본다. 인가 코드가 가로채여도 원문을 모르면 토큰으로 바꿀 수 없다.
                    .requireProofKey(true)
                    // 동의 화면은 띄우지 않는다. 클라이언트가 워크벤치 자신이다.
                    .requireAuthorizationConsent(false)
                    .build(),
            )
            .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(properties.accessTokenTtl).build())
            .build()
        return InMemoryRegisteredClientRepository(console)
    }

    /**
     * 메타데이터가 내주기로 한 것만 알리게 한다.
     *
     * 라이브러리는 자기가 할 수 있는 것을 모두 적는다. 그대로 두면 쓰지 않는 grant 와 인증 방식,
     * 막아 둔 토큰 조회, 하지 않기로 한 DPoP · mTLS 까지 알린다.
     */
    private fun describeOnlyWhatIsOffered(metadata: OAuth2AuthorizationServerMetadata.Builder) {
        metadata
            .grantTypes { it.retainAll(OFFERED_GRANT_TYPES) }
            .tokenEndpointAuthenticationMethods { it.retainAll(OFFERED_CLIENT_AUTHENTICATION_METHODS) }
            .tokenRevocationEndpointAuthenticationMethods { it.retainAll(OFFERED_CLIENT_AUTHENTICATION_METHODS) }
            .claims { claims -> NOT_OFFERED_CLAIMS.forEach(claims::remove) }
    }

    /**
     * 접근 토큰을 만드는 쪽.
     *
     * 인코더를 여기서 직접 만든다. 빈으로 등록된 `JwtEncoder` 를 찾게 두면 `auth` 모듈의 HS256 인코더가 잡힌다.
     * `sub` 는 로그인한 주체의 이름이고, 그 이름은 워크벤치 사용자 UUID 다.
     */
    private fun tokenGenerator(signingKey: SigningKey, properties: AuthserverProperties): OAuth2TokenGenerator<out OAuth2Token> {
        val jwtGenerator = JwtGenerator(NimbusJwtEncoder(ImmutableJWKSet(JWKSet(signingKey.jwk))))
        jwtGenerator.setJwtCustomizer { context ->
            if (context.tokenType == OAuth2TokenType.ACCESS_TOKEN) {
                context.claims.audience(listOf(properties.audience))
            }
        }
        return DelegatingOAuth2TokenGenerator(jwtGenerator, OAuth2AccessTokenGenerator(), OAuth2RefreshTokenGenerator())
    }

    companion object {
        const val CONSOLE_CLIENT_ID = "workbench-console"

        /** 이 인가 서버가 내주는 grant. grant 를 더하면 여기에도 더해야 메타데이터에 나타난다. */
        private val OFFERED_GRANT_TYPES = setOf(AuthorizationGrantType.AUTHORIZATION_CODE.value)

        /** 클라이언트가 자기를 증명하는 방법. 지금은 비밀값을 헤더에 싣는 것 하나다. */
        private val OFFERED_CLIENT_AUTHENTICATION_METHODS = setOf(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.value)

        private val NOT_OFFERED_CLAIMS = listOf(
            OAuth2AuthorizationServerMetadataClaimNames.INTROSPECTION_ENDPOINT,
            OAuth2AuthorizationServerMetadataClaimNames.INTROSPECTION_ENDPOINT_AUTH_METHODS_SUPPORTED,
            OAuth2AuthorizationServerMetadataClaimNames.TLS_CLIENT_CERTIFICATE_BOUND_ACCESS_TOKENS,
            OAuth2AuthorizationServerMetadataClaimNames.DPOP_SIGNING_ALG_VALUES_SUPPORTED,
        )

        /** `auth` 모듈의 Google 로그인이 시작되는 경로. */
        private const val GOOGLE_LOGIN_PATH = "/oauth2/authorization/google"
    }
}
