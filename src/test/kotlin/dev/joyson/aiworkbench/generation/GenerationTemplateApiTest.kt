package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.generation.application.GenerationJobService
import dev.joyson.aiworkbench.generation.application.GenerationTemplateService
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import dev.joyson.aiworkbench.workspace.application.WorkspaceService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GenerationTemplateApiTest @Autowired constructor(
    private val mvc: MockMvc,
    private val users: UserRegistry,
    private val tokens: TokenService,
    private val templates: GenerationTemplateService,
    private val workspaces: WorkspaceService,
    private val jobs: GenerationJobService,
) : IntegrationTest() {

    private val textOption = """{"type":"text-generate","instructions":"너는 {{role}}이다","prompt":"{{request}}"}"""
    private val imageOption = """{"type":"image-generate","prompt":"입력한 이미지의 스타일을 바꿔라. {{style}}","size":{"type":"ratio","ratio":"1:1","resolution":"1k"}}"""
    private val sha = "a".repeat(64)

    private fun token(subject: String): Pair<UUID, String> {
        val user = users.resolveOrRegister(RegisterIdentityCommand.of("https://accounts.google.com", subject, "소유자", "owner@example.com"))
        return user.uuid to tokens.issueAccessToken(user.uuid, user.displayName).accessToken
    }

    private fun create(token: String, body: String) = mvc.post("/api/templates") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `옵션을 저장하고 응답에 종류와 채울 변수를 알려준 뒤 치환 결과로 생성한다`() {
        val (owner, token) = token("template-text-option")
        val workspace = assertNotNull(workspaces.findDefaultUuid(owner))
        val created = create(token, """{"name":"요약","option":$textOption}""").andExpect {
            status { isCreated() }
            jsonPath("$.type") { value("text-generate") }
            jsonPath("$.option.type") { value("text-generate") }
            jsonPath("$.option.instructions") { value("너는 {{role}}이다") }
            jsonPath("$.variables[0]") { value("role") }
            jsonPath("$.variables[1]") { value("request") }
            jsonPath("$.inputImageCount") { value(0) }
            jsonPath("$.model") { value(null) }
        }.andReturn()
        val templateUuid = JsonMapper.shared().readTree(created.response.contentAsString).get("uuid").stringValue()

        val submitted = mvc.post("/api/templates/$templateUuid/jobs") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"variables":{"role":"편집자","request":"요약해"},"model":"${SharedTestConfig.FAKE_TEXT_MODEL}"}"""
        }.andExpect { status { isAccepted() } }.andReturn()
        val jobUuid = UUID.fromString(JsonMapper.shared().readTree(submitted.response.contentAsString).get("uuid").stringValue())
        val option = assertNotNull(jobs.findOwned(jobUuid, workspace)).option as TextGenerateOption
        assertEquals("너는 편집자이다", option.instructions)
        assertEquals("요약해", option.prompt)
    }

    @Test
    fun `기본 모델을 저장하면 모델 없이 실행하고 다른 종류의 모델은 400 이다`() {
        val (owner, token) = token("template-default-model")
        val workspace = assertNotNull(workspaces.findDefaultUuid(owner))
        create(token, """{"name":"그림 모델","option":$textOption,"model":"${SharedTestConfig.FAKE_MODEL}"}""").andExpect { status { isBadRequest() } }
        val created = create(token, """{"name":"글 모델","option":$textOption,"model":"${SharedTestConfig.FAKE_TEXT_MODEL}"}""").andExpect {
            status { isCreated() }
            jsonPath("$.model") { value(SharedTestConfig.FAKE_TEXT_MODEL) }
        }.andReturn()
        val templateUuid = JsonMapper.shared().readTree(created.response.contentAsString).get("uuid").stringValue()
        val submitted = mvc.post("/api/templates/$templateUuid/jobs") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"variables":{"role":"편집자","request":"요약해"},"model":""}"""
        }.andExpect { status { isAccepted() } }.andReturn()
        val jobUuid = UUID.fromString(JsonMapper.shared().readTree(submitted.response.contentAsString).get("uuid").stringValue())
        assertEquals(SharedTestConfig.FAKE_TEXT_MODEL, assertNotNull(jobs.findOwned(jobUuid, workspace)).model)
    }

    @Test
    fun `받을 수 없는 입력 이미지 수와 종류 변경은 400 이다`() {
        val (_, token) = token("template-image-count")
        create(token, """{"name":"글","option":$textOption,"inputImageCount":1}""").andExpect { status { isBadRequest() } }
        val fixedReference = imageOption.dropLast(1) + ""","sources":[{"type":"uploaded","sha256":"$sha"}]}"""
        create(token, """{"name":"섞기","option":$fixedReference,"inputImageCount":1}""").andExpect { status { isBadRequest() } }

        val created = create(token, """{"name":"스타일 수정","option":$imageOption,"inputImageCount":1}""").andExpect {
            status { isCreated() }
            jsonPath("$.type") { value("image-generate") }
            jsonPath("$.inputImageCount") { value(1) }
        }.andReturn()
        val path = "/api/templates/" + JsonMapper.shared().readTree(created.response.contentAsString).get("uuid").stringValue()

        mvc.put(path) { header("Authorization", "Bearer $token"); contentType = MediaType.APPLICATION_JSON; content = """{"name":"글로","option":$textOption}""" }
            .andExpect { status { isBadRequest() } }
        mvc.post("$path/jobs") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"variables":{"style":"수채화"},"model":"${SharedTestConfig.FAKE_MODEL}"}"""
        }.andExpect { status { isBadRequest() } }
        mvc.post("$path/jobs") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"variables":{"style":"수채화"},"images":[{"type":"uploaded","sha256":"$sha"}],"model":"${SharedTestConfig.FAKE_MODEL}"}"""
        }.andExpect { status { isBadRequest() }; jsonPath("$.sourceSha256") { value(sha) } }
    }

    @Test
    fun `템플릿 실행은 소유자와 변수를 검사하고 생성 작업을 접수한다`() {
        val (owner, ownerToken) = token("template-api-owner")
        val (_, otherToken) = token("template-api-other")
        val template = templates.create(owner, "편집자", TextGenerateOption(instructions = "너는 {{role}}이다", prompt = "요약해"), 0, assertNotNull(workspaces.findDefaultUuid(owner)))
        val url = "/api/templates/${template.uuid}/jobs"
        mvc.get("/api/templates/${template.uuid}") { header("Authorization", "Bearer $otherToken") }.andExpect { status { isNotFound() } }
        mvc.post(url) {
            header("Authorization", "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"model":"${SharedTestConfig.FAKE_TEXT_MODEL}","variables":{"role":"편집자"}}"""
        }.andExpect { status { isAccepted() }; jsonPath("$.uuid") { exists() } }
        mvc.post(url) { header("Authorization", "Bearer $ownerToken"); contentType = MediaType.APPLICATION_JSON; content = """{"model":"${SharedTestConfig.FAKE_TEXT_MODEL}"}""" }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `서비스 범위와 소유권을 모든 템플릿 요청에 적용한다`() {
        val (owner, token) = token("template-api-service-owner")
        val (_, otherToken) = token("template-api-service-other")
        val serviceA = workspaces.create(owner, "A").uuid
        val serviceB = workspaces.create(owner, "B").uuid
        val template = assertNotNull(templates.create(owner, "템플릿", TextGenerateOption(prompt = "요약해"), 0, serviceA))
        val body = """{"name":"이름","option":{"type":"text-generate","prompt":"요약해"}}"""
        val path = "/api/templates/${template.uuid}"
        mvc.get(path) { header("Authorization", "Bearer $token"); header("X-Workspace-Id", serviceA.toString()) }.andExpect { status { isOk() }; jsonPath("$.serviceUuid") { value(serviceA.toString()) } }
        mvc.get(path) { header("Authorization", "Bearer $token"); header("X-Workspace-Id", serviceB.toString()) }.andExpect { status { isNotFound() } }
        mvc.get("/api/templates") { header("Authorization", "Bearer $otherToken"); header("X-Workspace-Id", serviceA.toString()) }.andExpect { status { isNotFound() } }
        mvc.post("/api/templates") { header("Authorization", "Bearer $otherToken"); header("X-Workspace-Id", serviceA.toString()); contentType = MediaType.APPLICATION_JSON; content = body }.andExpect { status { isNotFound() } }
        mvc.put(path) { header("Authorization", "Bearer $token"); header("X-Workspace-Id", serviceB.toString()); contentType = MediaType.APPLICATION_JSON; content = body }.andExpect { status { isNotFound() } }
        mvc.delete(path) { header("Authorization", "Bearer $token"); header("X-Workspace-Id", serviceB.toString()) }.andExpect { status { isNotFound() } }
        mvc.post("$path/jobs") { header("Authorization", "Bearer $token"); header("X-Workspace-Id", serviceB.toString()); contentType = MediaType.APPLICATION_JSON; content = """{"model":"${SharedTestConfig.FAKE_TEXT_MODEL}"}""" }.andExpect { status { isNotFound() } }
    }
}
