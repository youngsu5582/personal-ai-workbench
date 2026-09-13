package dev.joyson.aiworkbench.auth.api

import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.auth.application.WorkbenchOidcUser
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * 로그인이 끝나면 곧바로 내 access token 을 발급한다. 이후 Google 은 흐름에서 빠진다.
 *
 * 이 클래스가 api 계층에 있는 이유: 하는 일이 전부 HTTP 기계장치다 —
 * Set-Cookie 헤더, 리다이렉트, 세션 폐기. 토큰 발급이라는 유스케이스 자체는
 * [TokenService] 가 갖고, 여기는 그 결과를 HTTP 응답으로 옮기는 변환만 한다.
 *
 * 토큰을 리다이렉트 URL 에 실어 보내지 않는 이유: 브라우저 히스토리·서버 로그·Referer 로 샌다.
 * 대신 짧은 수명의 HttpOnly 쿠키로 건네고, 페이지가 한 번 교환해 간다.
 * 교환 이후 토큰은 브라우저 메모리에만 있고 JS 가 읽을 수 있는 저장소에는 남지 않는다.
 */
@Component
class OAuth2LoginSuccessHandler(
    private val tokenService: TokenService,
    private val handoffCookie: HandoffCookie,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val principal = authentication.principal as WorkbenchOidcUser
        val issued = tokenService.issueAccessToken(principal.userUuid, principal.workbenchDisplayName)

        val cookie = handoffCookie.issue(issued.accessToken).toString()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie)

        // 세션은 여기까지만 쓴다. 이후 API 호출은 전부 Bearer 다.
        request.getSession(false)?.invalidate()
        response.sendRedirect("/?login=success")
    }
}
