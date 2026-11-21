package dev.joyson.aiworkbench.authserver

import dev.joyson.aiworkbench.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.Test

/**
 * 토큰 조회 엔드포인트가 닫혀 있는지 본다.
 *
 * 인증을 통과한 요청으로 불러야 "막혀서" 거절된 것인지 알 수 있다. 인증 없이 부르면 닫혀 있지 않아도 거절된다.
 */
class TokenIntrospectionClosedTest @Autowired constructor(
    private val mockMvc: MockMvc,
) : IntegrationTest() {

    @Test
    fun `토큰 조회는 인증된 요청에도 막혀 있다`() {
        mockMvc.post("/oauth2/introspect") {
            with(user("someone"))
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("token", "any-token")
        }.andExpect { status { isForbidden() } }
    }
}
