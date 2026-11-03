package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.generation.application.GenerationTemplateService
import dev.joyson.aiworkbench.generation.application.GenerationJobService
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import dev.joyson.aiworkbench.workspace.application.WorkspaceService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.delete
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class GenerationTemplateApiTest @Autowired constructor(
    private val mvc: MockMvc,
    private val users: UserRegistry,
    private val tokens: TokenService,
    private val templates: GenerationTemplateService,
    private val workspaces: WorkspaceService,
    private val jobs: GenerationJobService,
) : IntegrationTest() {
    @Test
    fun `시스템 프롬프트를 저장하고 변수 치환 결과로 생성한다`() {
        val owner = users.resolveOrRegister(RegisterIdentityCommand.of("https://accounts.google.com", "template-system-prompt", "소유자", "owner@example.com"))
        val token = tokens.issueAccessToken(owner.uuid, owner.displayName).accessToken
        val workspace = assertNotNull(workspaces.findDefaultUuid(owner.uuid))
        val created = mvc.post("/api/templates") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"요약","systemPrompt":"너는 {{role}}이다"}"""
        }.andExpect { status { isCreated() }; jsonPath("$.systemPrompt") { value("너는 {{role}}이다") }; jsonPath("$.instructions") { doesNotExist() } }.andReturn()
        val templateUuid = JsonMapper.shared().readTree(created.response.contentAsString).get("uuid").stringValue()
        val submitted = mvc.post("/api/templates/$templateUuid/jobs") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"prompt":"요약해","model":"${SharedTestConfig.FAKE_TEXT_MODEL}","variables":{"role":"편집자"}}"""
        }.andExpect { status { isAccepted() } }.andReturn()
        val jobUuid = UUID.fromString(JsonMapper.shared().readTree(submitted.response.contentAsString).get("uuid").stringValue())
        val option = assertNotNull(jobs.findOwned(jobUuid, workspace)).option as TextGenerateOption
        assertEquals("너는 편집자이다", option.instructions)
    }
    @Test
    fun `템플릿 실행은 소유자와 변수를 검사하고 생성 작업을 접수한다`() {
        val owner = users.resolveOrRegister(RegisterIdentityCommand.of("https://accounts.google.com", "template-api-owner", "소유자", "owner@example.com"))
        val other = users.resolveOrRegister(RegisterIdentityCommand.of("https://accounts.google.com", "template-api-other", "다른 사용자", "other@example.com"))
        val template = templates.create(owner.uuid, "편집자", "너는 {{role}}이다", assertNotNull(workspaces.findDefaultUuid(owner.uuid)))
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

    @Test
    fun `서비스 범위와 소유권을 모든 템플릿 요청에 적용한다`() {
        val owner = users.resolveOrRegister(RegisterIdentityCommand.of("https://accounts.google.com", "template-api-service-owner", "소유자", "owner@example.com"))
        val other = users.resolveOrRegister(RegisterIdentityCommand.of("https://accounts.google.com", "template-api-service-other", "다른 사용자", "other@example.com"))
        val serviceA = workspaces.create(owner.uuid, "A").uuid
        val serviceB = workspaces.create(owner.uuid, "B").uuid
        val template = assertNotNull(templates.create(owner.uuid, "템플릿", "지시", serviceA))
        val token = tokens.issueAccessToken(owner.uuid, owner.displayName).accessToken
        val otherToken = tokens.issueAccessToken(other.uuid, other.displayName).accessToken
        val path = "/api/templates/${template.uuid}"
        mvc.get(path) { header("Authorization", "Bearer $token"); header("X-Workspace-Id", serviceA.toString()) }.andExpect { status { isOk() }; jsonPath("$.serviceUuid") { value(serviceA.toString()) } }
        mvc.get(path) { header("Authorization", "Bearer $token"); header("X-Workspace-Id", serviceB.toString()) }.andExpect { status { isNotFound() } }
        mvc.get("/api/templates") { header("Authorization", "Bearer $otherToken"); header("X-Workspace-Id", serviceA.toString()) }.andExpect { status { isNotFound() } }
        mvc.post("/api/templates") { header("Authorization", "Bearer $otherToken"); header("X-Workspace-Id", serviceA.toString()); contentType = MediaType.APPLICATION_JSON; content = """{"name":"이름","systemPrompt":"지시"}""" }.andExpect { status { isNotFound() } }
        mvc.put(path) { header("Authorization", "Bearer $token"); header("X-Workspace-Id", serviceB.toString()); contentType = MediaType.APPLICATION_JSON; content = """{"name":"변경","systemPrompt":"지시"}""" }.andExpect { status { isNotFound() } }
        mvc.delete(path) { header("Authorization", "Bearer $token"); header("X-Workspace-Id", serviceB.toString()) }.andExpect { status { isNotFound() } }
        mvc.post("$path/jobs") { header("Authorization", "Bearer $token"); header("X-Workspace-Id", serviceB.toString()); contentType = MediaType.APPLICATION_JSON; content = """{"prompt":"질문","model":"${SharedTestConfig.FAKE_TEXT_MODEL}"}""" }.andExpect { status { isNotFound() } }
    }
}
