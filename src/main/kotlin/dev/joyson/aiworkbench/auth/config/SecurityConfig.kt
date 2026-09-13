package dev.joyson.aiworkbench.auth.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * `/api` 이하는 내가 발급한 JWT 로만 접근한다. 무상태이며 브라우저·CLI·MCP 가 같은 경로를 쓴다.
 *
 * 아직 로그인 수단이 없으므로 토큰을 받는 경로는 여기 없다.
 * 그 경로(OAuth2 로그인)는 별도 필터 체인으로 뒤에 붙는다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { rs -> rs.jwt { } }
        return http.build()
    }

}
