package dev.joyson.aiworkbench.auth.api

import dev.joyson.aiworkbench.auth.AuthProperties
import dev.joyson.aiworkbench.auth.domain.AuthErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 로그인 직후 단 한 번, HttpOnly 쿠키에 담긴 access token 을 응답 바디로 바꿔준다.
 *
 * 이렇게 하는 이유는 토큰을 URL 에도, JS 가 읽을 수 있는 저장소에도 두지 않기 위해서다.
 * 교환 즉시 쿠키를 만료시켜 한 번만 쓰이게 한다.
 */
@RestController
class AuthController(
    private val properties: AuthProperties,
    private val handoffCookie: HandoffCookie,
) {

    @GetMapping(HandoffCookie.PATH)
    fun handoff(request: HttpServletRequest): ResponseEntity<HandoffResponse> {
        val token = extractToken(request)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, handoffCookie.expire().toString())
            .body(
                HandoffResponse(
                    accessToken = token,
                    expiresIn = properties.accessTokenTtl.seconds,
                ),
            )
    }

    private fun extractToken(request: HttpServletRequest): String {
        val token = request.cookies
            ?.firstOrNull { it.name == properties.handoffCookieName }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: throw HandoffTokenNotFoundException()
        return token
    }
}

/**
 * 교환 응답. OAuth 2.0 토큰 응답(RFC 6749 §5.1)의 필드 이름을 따른다.
 *
 * `tokenType` 은 지금 항상 Bearer 지만, 클라이언트가 헤더를 조립할 때 쓰는 값이라
 * 하드코딩하지 않고 응답에 실어 보낸다.
 */
data class HandoffResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
)

@ResponseStatus(HttpStatus.UNAUTHORIZED)
class HandoffTokenNotFoundException :
    RuntimeException(AuthErrorCode.NO_HANDOFF_TOKEN.message) {
    val code: String = AuthErrorCode.NO_HANDOFF_TOKEN.code
}
