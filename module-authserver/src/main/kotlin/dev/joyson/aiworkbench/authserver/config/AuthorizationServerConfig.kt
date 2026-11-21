package dev.joyson.aiworkbench.authserver.config

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import dev.joyson.aiworkbench.authserver.AuthserverProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationServerMetadata
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationServerMetadataClaimNames
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.web.SecurityFilterChain

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
    fun authorizationServerSecurityFilterChain(http: HttpSecurity, settings: AuthorizationServerSettings): SecurityFilterChain {
        http
            .oauth2AuthorizationServer { server ->
                http.securityMatcher(server.endpointsMatcher)
                server.authorizationServerMetadataEndpoint { endpoint ->
                    endpoint.authorizationServerMetadataCustomizer { metadata -> describeOnlyWhatIsOffered(metadata) }
                }
            }
            .authorizeHttpRequests {
                // 토큰 조회는 쓰지 않는다. API 는 JWKS 로 토큰을 직접 검증한다. 라이브러리에 끄는 설정이 없어 여기서 막는다.
                it.requestMatchers(settings.tokenIntrospectionEndpoint).denyAll()
                it.anyRequest().authenticated()
            }
        return http.build()
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

    /** JWKS 엔드포인트는 이 키의 공개 부분만 내보낸다. */
    @Bean
    fun jwkSource(properties: AuthserverProperties): JWKSource<SecurityContext> =
        ImmutableJWKSet(JWKSet(SigningKey(properties.signingKey).jwk))

    /** 등록된 클라이언트가 아직 없다. 어떤 `client_id` 로 와도 모르는 클라이언트다. */
    @Bean
    fun registeredClientRepository(): RegisteredClientRepository = object : RegisteredClientRepository {
        override fun save(registeredClient: RegisteredClient) =
            throw UnsupportedOperationException("클라이언트를 등록하는 길이 아직 없다")

        override fun findById(id: String): RegisteredClient? = null

        override fun findByClientId(clientId: String): RegisteredClient? = null
    }

    private companion object {
        /** 이 인가 서버가 내주는 grant. grant 를 더하면 여기에도 더해야 메타데이터에 나타난다. */
        val OFFERED_GRANT_TYPES = setOf(AuthorizationGrantType.AUTHORIZATION_CODE.value)

        /** 클라이언트가 자기를 증명하는 방법. 지금은 비밀값을 헤더에 싣는 것 하나다. */
        val OFFERED_CLIENT_AUTHENTICATION_METHODS = setOf(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.value)

        val NOT_OFFERED_CLAIMS = listOf(
            OAuth2AuthorizationServerMetadataClaimNames.INTROSPECTION_ENDPOINT,
            OAuth2AuthorizationServerMetadataClaimNames.INTROSPECTION_ENDPOINT_AUTH_METHODS_SUPPORTED,
            OAuth2AuthorizationServerMetadataClaimNames.TLS_CLIENT_CERTIFICATE_BOUND_ACCESS_TOKENS,
            OAuth2AuthorizationServerMetadataClaimNames.DPOP_SIGNING_ALG_VALUES_SUPPORTED,
        )
    }
}
