package dev.joyson.aiworkbench.auth

import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.Test

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


}
