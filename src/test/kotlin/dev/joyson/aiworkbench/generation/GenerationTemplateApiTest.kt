package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.generation.application.GenerationTemplateService
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.Test

class GenerationTemplateApiTest @Autowired constructor(
    private val mvc: MockMvc,
    private val users: UserRegistry,
    private val tokens: TokenService,
    private val templates: GenerationTemplateService,
) : IntegrationTest() {
    @Test
    fun `템플릿 실행은 소유자와 변수를 검사하고 생성 작업을 접수한다`() {
        val owner = users.resolveOrRegister(RegisterIdentityCommand.of("https://accounts.google.com", "template-api-owner", "소유자", "owner@example.com"))
        val other = users.resolveOrRegister(RegisterIdentityCommand.of("https://accounts.google.com", "template-api-other", "다른 사용자", "other@example.com"))
        val template = templates.create(owner.uuid, "편집자", "너는 {{role}}이다")
        val ownerToken = tokens.issueAccessToken(owner.uuid, owner.displayName).accessToken
        val otherToken = tokens.issueAccessToken(other.uuid, other.displayName).accessToken
        val url = "/api/templates/${template.uuid}/jobs"
        val request = """{"prompt":"요약해","model":"${SharedTestConfig.FAKE_TEXT_MODEL}","variables":{"role":"편집자"}}"""
        mvc.get("/api/templates/${template.uuid}") { header("Authorization", "Bearer $otherToken") }.andExpect { status { isNotFound() } }
        mvc.post(url) { header("Authorization", "Bearer $ownerToken"); contentType = MediaType.APPLICATION_JSON; content = request }
            .andExpect { status { isAccepted() }; jsonPath("$.uuid") { exists() } }
        mvc.post(url) { header("Authorization", "Bearer $ownerToken"); contentType = MediaType.APPLICATION_JSON; content = """{"prompt":"요약해","model":"${SharedTestConfig.FAKE_TEXT_MODEL}"}""" }
            .andExpect { status { isBadRequest() } }
    }
}
