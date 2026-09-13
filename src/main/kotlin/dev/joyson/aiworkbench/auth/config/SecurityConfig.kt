package dev.joyson.aiworkbench.auth.config

import dev.joyson.aiworkbench.auth.api.HandoffCookie
import dev.joyson.aiworkbench.auth.api.OAuth2LoginSuccessHandler
import dev.joyson.aiworkbench.auth.application.OidcUserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * 두 개의 필터 체인이 공존한다.
 *
 *   `/api` 이하 → Bearer(내 JWT), 무상태. 브라우저·CLI·MCP 가 모두 같은 경로를 쓴다.
 *   그 외        → OAuth2 로그인. Google 로 갔다 오는 리다이렉트 구간에서만 세션을 쓴다.
 *
 * "완전 stateless" 가 아닌 이유는 state/PKCE 를 리다이렉트 왕복 동안 어딘가 보관해야 하기 때문이다.
 * 세션은 로그인 구간에서만 살고, 토큰이 발급되는 순간 폐기된다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    @Order(1)
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { rs -> rs.jwt { } }
        return http.build()
    }

    @Bean
    @Order(2)
    fun loginSecurityFilterChain(
        http: HttpSecurity,
        oidcUserService: OidcUserService,
        successHandler: OAuth2LoginSuccessHandler,
    ): SecurityFilterChain {
        http
            // 로그인 직후의 토큰 교환은 쿠키로만 인증되는 한 번짜리 GET 이라 CSRF 대상에서 뺀다.
            .csrf { it.ignoringRequestMatchers(HandoffCookie.PATH) }
            .authorizeHttpRequests {
                // "/error" 가 빠지면 컨트롤러가 던진 401 이 ERROR dispatch 로 /error 에 재진입하고,
                // 그게 인증 대상이라 로그인 진입점이 302 로 바꿔버린다. 상태 코드가 조용히 뒤집힌다.
                it.requestMatchers("/", "/index.html", "/favicon.ico", "/error", HandoffCookie.PATH, "/login/**", "/oauth2/**")
                    .permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2Login { login ->
                login.userInfoEndpoint { userInfo -> userInfo.oidcUserService(oidcUserService) }
                login.successHandler(successHandler)
                login.failureUrl("/?login=failed")
            }
        return http.build()
    }
}
