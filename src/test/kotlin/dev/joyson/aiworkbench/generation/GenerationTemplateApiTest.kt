package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.generation.application.GenerationJobService
import dev.joyson.aiworkbench.generation.application.GenerationTemplateService
import dev.joyson.aiworkbench.generation.domain.option.AudioGenerateOption
import dev.joyson.aiworkbench.generation.domain.option.FileSource
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.Sha256
import dev.joyson.aiworkbench.upload.domain.UploadedFile
import dev.joyson.aiworkbench.upload.domain.UploadedFileMetadata
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
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
    private val uploadedFiles: UploadedFileRepository,
    private val fileStorage: FileStorage,
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
            jsonPath("$.inputFileCount") { value(0) }
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

    /** 기본 모델을 정하면 저장할 때 목소리까지 확인한다. 실행하면 글과 말투에 변수가 채워진 TTS 작업이 된다. */
    @Test
    fun `TTS 템플릿은 목소리를 확인해 저장하고 변수를 채워 실행한다`() {
        val (owner, token) = token("template-tts")
        val workspace = assertNotNull(workspaces.findDefaultUuid(owner))
        val model = SharedTestConfig.FAKE_AUDIO_MODEL
        create(token, """{"name":"질문","option":{"type":"audio-generate","text":"{{question}}","voice":"nobody"},"model":"$model"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.availableVoices[0]") { value(SharedTestConfig.FAKE_VOICE) }
        }
        val created = create(token, """{"name":"질문","option":{"type":"audio-generate","text":"{{question}}","voice":"${SharedTestConfig.FAKE_VOICE}","style":"{{tone}}"},"model":"$model"}""").andExpect {
            status { isCreated() }
            jsonPath("$.type") { value("audio-generate") }
            jsonPath("$.variables[0]") { value("question") }
            jsonPath("$.variables[1]") { value("tone") }
        }.andReturn()
        val templateUuid = JsonMapper.shared().readTree(created.response.contentAsString).get("uuid").stringValue()

        val submitted = mvc.post("/api/templates/$templateUuid/jobs") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"variables":{"question":"동시성은 어떻게 막으셨나요?","tone":"차분하게"}}"""
        }.andExpect { status { isAccepted() } }.andReturn()
        val jobUuid = UUID.fromString(JsonMapper.shared().readTree(submitted.response.contentAsString).get("uuid").stringValue())
        assertEquals(
            AudioGenerateOption(text = "동시성은 어떻게 막으셨나요?", voice = SharedTestConfig.FAKE_VOICE, style = "차분하게"),
            assertNotNull(jobs.findOwned(jobUuid, workspace)).option,
        )
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

    /** 옛 이름(`inputImageCount` · `images`)으로 보내도 받는다 — 템플릿을 만드는 바깥 스크립트가 아직 그 이름을 쓴다. */
    @Test
    fun `받을 수 없는 실행 파일 수와 종류 변경은 400 이고 옛 이름도 받는다`() {
        val (_, token) = token("template-image-count")
        create(token, """{"name":"글","option":$textOption,"inputFileCount":2}""").andExpect { status { isBadRequest() } }
        val fixedReference = imageOption.dropLast(1) + ""","sources":[{"type":"uploaded","sha256":"$sha"}]}"""
        create(token, """{"name":"섞기","option":$fixedReference,"inputImageCount":1}""").andExpect { status { isBadRequest() } }

        val created = create(token, """{"name":"스타일 수정","option":$imageOption,"inputImageCount":1}""").andExpect {
            status { isCreated() }
            jsonPath("$.type") { value("image-generate") }
            jsonPath("$.inputFileCount") { value(1) }
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

    /** STT 템플릿 — 글 템플릿이 실행 때 받은 오디오를 첨부로 붙여, 받아쓰기를 변수 없이 부를 수 있다. */
    @Test
    fun `글 템플릿은 실행 때 받은 오디오를 첨부로 붙인다`() {
        val (owner, token) = token("template-stt")
        val workspace = assertNotNull(workspaces.findDefaultUuid(owner))
        val audio = uploadedWav(owner, workspace)
        val body = """{"name":"받아쓰기","option":{"type":"text-generate","prompt":"들리는 그대로 받아 써"},"inputFileCount":1,"model":"${SharedTestConfig.FAKE_TEXT_MODEL}"}"""
        val created = create(token, body).andExpect {
            status { isCreated() }
            jsonPath("$.inputFileCount") { value(1) }
        }.andReturn()
        val templateUuid = JsonMapper.shared().readTree(created.response.contentAsString).get("uuid").stringValue()

        val submitted = mvc.post("/api/templates/$templateUuid/jobs") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"files":[{"type":"uploaded","sha256":"$audio"}]}"""
        }.andExpect { status { isAccepted() } }.andReturn()
        val jobUuid = UUID.fromString(JsonMapper.shared().readTree(submitted.response.contentAsString).get("uuid").stringValue())
        val option = assertNotNull(jobs.findOwned(jobUuid, workspace)).option as TextGenerateOption
        assertEquals(listOf<FileSource>(FileSource.Uploaded(audio)), option.attachments)
    }

    /** 올린 WAV 한 개를 보관소에 둔다. 처음 참조될 때 앞부분 바이트로 형식을 판정한다. */
    private fun uploadedWav(owner: UUID, workspace: UUID): String {
        val wav = "RIFF".toByteArray() + byteArrayOf(0x24, 0, 0, 0) + "WAVE".toByteArray()
        val sha256 = Sha256.of(wav).hex
        val file = uploadedFiles.save(
            UploadedFile(
                sha256 = sha256,
                ownerUserUuid = owner,
                workspaceUuid = workspace,
                storageKey = "workspaces/$workspace/uploads/$sha256",
                metadata = UploadedFileMetadata(mimeType = "audio/wav", fileSize = wav.size.toLong()),
            ),
        )
        fileStorage.put(file.storageKey, wav, "audio/wav")
        return sha256
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
