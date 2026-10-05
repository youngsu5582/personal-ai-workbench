package dev.joyson.aiworkbench.upload

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.Test

class UploadUnsupportedApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val tokenService: TokenService,
    private val userRegistry: UserRegistry,
) : IntegrationTest() {
    @Test
    fun `직접 업로드를 지원하지 않는 보관소면 501 이다`() {
        val user = userRegistry.resolveOrRegister(
            RegisterIdentityCommand.of(
                issuer = "https://accounts.google.com",
                subject = "upload-unsupported-test",
                displayName = "upload-owner",
                email = "upload-local@example.com",
            ),
        )
        val token = tokenService.issueAccessToken(user.uuid, user.displayName).accessToken

        mockMvc.post("/api/uploads") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"image/png","contentLength":1}"""
        }.andExpect {
            status { isNotImplemented() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("현재 보관소는 직접 업로드를 지원하지 않는다") }
        }
    }
}
