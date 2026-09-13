package dev.joyson.aiworkbench.auth.api

import dev.joyson.aiworkbench.auth.AuthProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 로그인 직후 access token 을 브라우저에 건네는 한 번짜리 쿠키.
 *
 * 굽는 쪽(OAuth2LoginSuccessHandler)과 지우는 쪽([AuthController])이 한곳을 쓰도록 모았다.
 * 브라우저는 (name, path, domain) 이 **모두** 일치해야 기존 쿠키를 덮거나 지운다.
 * 한쪽만 바꾸면 "지웠다고 생각했는데 남아 있는" 쿠키가 생기고,
 * 한 번만 쓰여야 할 토큰이 TTL 내내 재사용 가능해진다.
 */
@Component
class HandoffCookie(
    private val properties: AuthProperties,
) {
    fun issue(token: String): ResponseCookie =
        base(token).maxAge(properties.handoffCookieTtl).build()

    fun expire(): ResponseCookie =
        base(EMPTY).maxAge(EXPIRE_IMMEDIATELY).build()

    private fun base(value: String) = ResponseCookie.from(properties.handoffCookieName, value)
        // JS 가 읽을 수 없다. XSS 가 있어도 이 쿠키로는 토큰이 새지 않는다.
        .httpOnly(true)
        // 운영(HTTPS)에서는 반드시 true. localhost 는 http 라 false 로 둔다.
        .secure(properties.cookieSecure)
        .sameSite(SAME_SITE_LAX)
        .path(PATH)

    companion object {
        /**
         * 이 쿠키가 붙는 유일한 경로.
         *
         * 루트로 두면 `/api` 이하와 정적 파일 요청에도 매번 실려 간다 — 쓸 데가 없는데 노출만 늘어난다.
         * 교환 엔드포인트 하나로 좁히면 그 외 요청에는 아예 전송되지 않는다.
         */
        const val PATH = "/auth/handoff"

        /**
         * 크로스 사이트 요청에 쿠키를 붙일지에 대한 규칙.
         *
         * - Strict: 어떤 크로스 사이트 요청에도 보내지 않는다. 다만 Spring 세션 쿠키까지 Strict 면
         *   Google 이 되돌려 보내는 콜백(크로스 사이트 top-level GET)에 세션이 실리지 않아
         *   저장해둔 state/PKCE 를 찾지 못한다.
         * - Lax: top-level 네비게이션(GET)에만 보낸다. img·fetch·iframe 같은 서브리소스 요청에는
         *   보내지 않으므로 공격자 페이지가 이 쿠키를 실어 보낼 수 없다.
         * - None: 항상 보낸다. Secure 필수. 여기서 쓸 이유가 없다.
         */
        const val SAME_SITE_LAX = "Lax"

        private val EXPIRE_IMMEDIATELY: Duration = Duration.ZERO
        private const val EMPTY: String = ""
    }
}
