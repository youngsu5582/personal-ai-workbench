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
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
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
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .oauth2AuthorizationServer { server -> http.securityMatcher(server.endpointsMatcher) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
        return http.build()
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
}
