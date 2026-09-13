package dev.joyson.aiworkbench.auth

import dev.joyson.aiworkbench.auth.api.HandoffCookie
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Google 을 호출하지 않고 "발급된 토큰으로 보호된 API 가 열리는가" 만 검증한다.
 *
 * 로그인 흐름(리다이렉트·code 교환)은 Spring 의 책임이므로 여기서 테스트하지 않는다.
 * 내가 책임지는 경계는 토큰 발급과 그 토큰으로의 소유자 해석이다.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class TokenAuthenticationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val tokenService: TokenService,
    private val userRegistry: UserRegistry,
) {

    @Test
    fun `토큰이 없으면 401 이다`() {
        mockMvc.get("/api/me").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `잘못된 토큰이면 401 이다`() {
        mockMvc.get("/api/me") {
            header("Authorization", "Bearer not-a-real-token")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `발급한 토큰으로 소유자를 해석한다`() {
        val user = userRegistry.resolveOrRegister(
            RegisterIdentityCommand.of(
                issuer = "https://accounts.google.com",
                subject = "sub-token-test",
                displayName = "Joyson",
                email = "joyson@example.com",
            ),
        )
        val issued = tokenService.issueAccessToken(user.uuid, user.displayName)

        mockMvc.get("/api/me") {
            header("Authorization", "Bearer ${issued.accessToken}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.uuid") { value(user.uuid.toString()) }
            jsonPath("$.displayName") { value("Joyson") }
            jsonPath("$.active") { value(true) }
            jsonPath("$.identities[0].issuer") { value("https://accounts.google.com") }
        }
    }

    @Test
    fun `로그인하지 않으면 handoff 는 401 이다`() {
        mockMvc.get(HandoffCookie.PATH).andExpect { status { isUnauthorized() } }
    }

    /**
     * 쿠키를 굽는 쪽과 지우는 쪽의 (name, path) 가 어긋나면 브라우저가 쿠키를 지우지 못하고,
     * 한 번만 쓰여야 할 토큰이 TTL 내내 재사용 가능해진다. 그 정합성을 여기서 고정한다.
     */
    @Test
    fun `핸드오프 쿠키를 토큰으로 교환하고 같은 경로로 즉시 만료시킨다`() {
        val issued = tokenService.issueAccessToken(UUID.randomUUID(), "Joyson")

        val result = mockMvc.get(HandoffCookie.PATH) {
            cookie(Cookie("wb_handoff", issued.accessToken))
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { value(issued.accessToken) }
            jsonPath("$.tokenType") { value("Bearer") }
            jsonPath("$.expiresIn") { value(900) }
        }.andReturn()

        val setCookie = result.response.getHeader("Set-Cookie")!!
        assertTrue(setCookie.contains("Max-Age=0"), "쿠키가 즉시 만료되어야 한다: $setCookie")
        assertTrue(setCookie.contains("Path=${HandoffCookie.PATH}"), "만료 쿠키의 경로가 발급과 같아야 한다: $setCookie")
        assertTrue(setCookie.contains("HttpOnly"), "HttpOnly 가 유지되어야 한다: $setCookie")
    }
}
