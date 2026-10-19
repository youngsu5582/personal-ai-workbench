package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.generation.domain.GeneratedText
import dev.joyson.aiworkbench.generation.domain.GeneratedTextRepository
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.storage.Sha256
import dev.joyson.aiworkbench.storage.BlobKey
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 폴링으로 보이는 것과 보이면 안 되는 것을 고정한다.
 *
 * 남의 Job 을 403 으로 거절하면 "그 uuid 는 존재한다" 가 새어 나간다. 그래서 404 다 —
 * 없는 것과 남의 것이 바깥에서 구분되지 않아야 한다.
 *
 * 테스트 프로필은 워커가 꺼져 있어(`workbench.generation.worker.enabled: false`)
 * 접수한 Job 이 그대로 머문다. 그래서 진행 상황이 결정적으로 관측된다.
 */
class GenerationJobQueryApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val tokenService: TokenService,
    private val userRegistry: UserRegistry,
    private val jobRepository: GenerationJobTestRepository,
    private val taskRepository: GenerationJobTaskTestRepository,
    private val fileRepository: GeneratedFileTestRepository,
    private val fileStorage: FileStorage,
    private val textRepository: GeneratedTextRepository,
) : IntegrationTest() {

    private fun tokenOf(subject: String): String {
        val user = userRegistry.resolveOrRegister(
            RegisterIdentityCommand.of(
                issuer = "https://accounts.google.com",
                subject = subject,
                displayName = subject,
                email = "$subject@example.com",
            ),
        )
        return tokenService.issueAccessToken(user.uuid, user.displayName).accessToken
    }

    private fun submit(token: String, taskCount: Int, option: String = OPTION, model: String = FAKE_MODEL): String =
        mockMvc.post("/api/jobs") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"option":$option,"model":"$model","taskCount":$taskCount}"""
        }.andReturn().response.contentAsString
            .substringAfter("\"uuid\":\"").substringBefore("\"")

    private fun query(uuid: String, token: String?) =
        mockMvc.get("/api/jobs/$uuid") {
            if (token != null) header("Authorization", "Bearer $token")
        }

    @Test
    fun `자기 Job 을 조회하면 수명과 진행 상황이 온다`() {
        val token = tokenOf("query-owner")
        val uuid = submit(token, taskCount = 2)

        query(uuid, token).andExpect {
            status { isOk() }
            jsonPath("$.uuid") { value(uuid) }
            jsonPath("$.status") { value("DISPATCHED") }
            jsonPath("$.model") { value(FAKE_MODEL) }
            // 워커가 꺼져 있으므로 아무것도 끝나지 않았다.
            jsonPath("$.progress.total") { value(2) }
            jsonPath("$.progress.succeeded") { value(0) }
            jsonPath("$.progress.failed") { value(0) }
            jsonPath("$.progress.remaining") { value(2) }
        }
    }

    @Test
    fun `남의 Job 은 존재 여부조차 알려주지 않는다`() {
        val uuid = submit(tokenOf("query-owner-2"), taskCount = 1)

        query(uuid, tokenOf("query-stranger")).andExpect { status { isNotFound() } }
    }

    @Test
    fun `없는 Job 은 404 다`() {
        query(UUID.randomUUID().toString(), tokenOf("query-owner-3"))
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `토큰이 없으면 401 이다`() {
        val uuid = submit(tokenOf("query-owner-4"), taskCount = 1)

        query(uuid, token = null).andExpect { status { isUnauthorized() } }
    }

    /**
     * 한 장은 성공하고 한 장은 실패한 Job 을 만든다.
     *
     * 워커가 꺼져 있어(`worker.enabled: false`) 상태를 직접 옮긴다.
     * RUNNING 을 거치는 것은 전이 규칙이 그렇게 정해져 있기 때문이다.
     */
    private fun jobWithOneSuccessAndOneFailure(token: String): Pair<String, ByteArray> {
        val jobUuid = submit(token, taskCount = 2)
        val job = jobRepository.findByUuid(UUID.fromString(jobUuid))!!
        val tasks = taskRepository.findAllByJobIdOrderBySequence(job.id!!)

        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        // 워커와 같은 방식으로 키를 만든다 — 자리는 내용이 정하고, 행 식별자는 따로 둔다.
        val fileUuid = Sha256.of(bytes).hex
        val key = BlobKey.of(job.ownerUserUuid, Sha256.of(bytes), MIME).value
        fileStorage.put(key, bytes, MIME)

        succeed(tasks[0])
        fileRepository.save(
            GeneratedFile(
                sha256 = fileUuid,
                taskId = tasks[0].id!!,
                storageKey = key,
                metadata = FileMetadata(width = 1024, height = 1024, mimeType = MIME, fileSize = bytes.size),
            ),
        )
        fail(tasks[1], "rate limit")

        return jobUuid to bytes
    }

    private fun succeed(task: GenerationJobTask) {
        task.transitionTo(TaskStatus.RUNNING)
        task.transitionTo(TaskStatus.SUCCEEDED)
        taskRepository.save(task)
    }

    private fun fail(task: GenerationJobTask, reason: String) {
        task.transitionTo(TaskStatus.RUNNING)
        task.transitionTo(TaskStatus.FAILED, failureReason = reason)
        taskRepository.save(task)
    }

    @Test
    fun `실패한 Task 도 자리를 지키고 성공한 Task 는 파일 주소를 준다`() {
        val token = tokenOf("files-owner")
        val (jobUuid, _) = jobWithOneSuccessAndOneFailure(token)

        query(jobUuid, token).andExpect {
            status { isOk() }
            jsonPath("$.progress.succeeded") { value(1) }
            jsonPath("$.progress.failed") { value(1) }
            jsonPath("$.progress.remaining") { value(0) }

            jsonPath("$.tasks.length()") { value(2) }
            jsonPath("$.tasks[0].status") { value("SUCCEEDED") }
            jsonPath("$.tasks[0].files.length()") { value(1) }
            jsonPath("$.tasks[0].files[0].url") { exists() }
            // 테스트 환경은 로컬 디스크라 서명할 수 없다. 이 값이 null 이라는 것이
            // "받는 쪽은 url 로 폴백해야 한다" 는 계약이다.
            jsonPath("$.tasks[0].files[0].previewUrl") { doesNotExist() }
            jsonPath("$.tasks[0].files[0].width") { value(1024) }

            // 실패한 자리가 목록에서 사라지지 않는다 — 이것이 파일만 평평하게 담지 않는 이유다.
            jsonPath("$.tasks[1].status") { value("FAILED") }
            jsonPath("$.tasks[1].failureReason") { value("rate limit") }
            jsonPath("$.tasks[1].files.length()") { value(0) }
            // 글 결과물은 null 이 아니라 빈 목록이다. "해당 없음" 과 "아직 없음" 을 가를 필요가 없다.
            jsonPath("$.tasks[0].texts.length()") { value(0) }
        }
    }

    /** 워커가 꺼져 있어 성공을 직접 옮기고 글을 넣는다. 워커가 쓰는 것과 같은 모양이다. */
    private fun textJobAnswered(token: String, option: String, content: String): String {
        val jobUuid = submit(token, taskCount = 1, option = option, model = FAKE_TEXT_MODEL)
        val job = jobRepository.findByUuid(UUID.fromString(jobUuid))!!
        val task = taskRepository.findAllByJobIdOrderBySequence(job.id!!).single()
        succeed(task)
        textRepository.save(GeneratedText(taskId = task.id!!, content = content))
        return jobUuid
    }

    @Test
    fun `형식 없는 글은 문자열로 준다`() {
        val token = tokenOf("text-owner")
        val jobUuid = textJobAnswered(token, """{"type":"text-generate","prompt":"요약해"}""", "세 줄 요약")

        query(jobUuid, token).andExpect {
            status { isOk() }
            jsonPath("$.option.type") { value("text-generate") }
            jsonPath("$.tasks[0].texts[0].content") { value("세 줄 요약") }
            jsonPath("$.tasks[0].files.length()") { value(0) }
        }
    }

    /**
     * 구조화된 답은 객체로 준다. 키는 모델이 낸 순서 그대로다 — jsonb 를 거쳤다면 짧은 키(`answer`)가 앞으로 왔다.
     * 옵션의 스키마도 받은 순서 그대로 문자열로 되돌아온다.
     */
    @Test
    fun `구조화된 답은 객체로 주고 키 순서를 지킨다`() {
        val token = tokenOf("schema-owner")
        val schema = """{"type":"object","properties":{"reasoning":{"type":"string"},"answer":{"type":"string"}}}"""
        val option = """{"type":"text-generate","prompt":"풀어","output":{"type":"json-schema","schema":$schema}}"""
        val jobUuid = textJobAnswered(token, option, """{"reasoning":"6 곱하기 7","answer":"42"}""")

        val body = query(jobUuid, token)
            .andExpect {
                status { isOk() }
                jsonPath("$.tasks[0].texts[0].content.answer") { value("42") }
            }
            .andReturn().response.contentAsString

        assertTrue(body.contains(""""content":{"reasoning":"6 곱하기 7","answer":"42"}"""), body)
        assertTrue(body.contains(""""schema":${jsonString(schema)}"""), "스키마의 키 순서가 저장을 거치며 바뀌었다: $body")
    }

    /** 문자열로 직렬화된 모양. 스키마에 따옴표 말고는 escape 할 글자가 없다. */
    private fun jsonString(raw: String) = "\"" + raw.replace("\"", "\\\"") + "\""

    @Test
    fun `응답이 준 주소로 파일을 받는다`() {
        val token = tokenOf("download-owner")
        val (jobUuid, bytes) = jobWithOneSuccessAndOneFailure(token)

        val url = fileUrlOf(jobUuid, token)
        val response = mockMvc.get(url) { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
            .andReturn().response

        assertEquals(MIME, response.contentType?.substringBefore(';'))
        assertContentEquals(bytes, response.contentAsByteArray)
        // 파일명은 형식에서 짓는다. 보관소 키가 해시로 바뀌어도 받는 쪽 이름은 그대로다.
        assertTrue(
            response.getHeader("Content-Disposition")!!.endsWith(""".png""""),
            response.getHeader("Content-Disposition")!!,
        )
    }

    @Test
    fun `남의 파일은 받을 수 없다`() {
        val token = tokenOf("download-owner-2")
        val (jobUuid, _) = jobWithOneSuccessAndOneFailure(token)

        val url = fileUrlOf(jobUuid, token)
        mockMvc.get(url) { header("Authorization", "Bearer ${tokenOf("download-stranger")}") }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `파일 목록은 해시와 해시 조회 주소를 주고 UUID 는 주지 않는다`() {
        val token = tokenOf("hash-list-owner")
        val (jobUuid, bytes) = jobWithOneSuccessAndOneFailure(token)
        val hash = Sha256.of(bytes).hex
        query(jobUuid, token).andExpect {
            status { isOk() }
            jsonPath("$.tasks[0].files[0].sha256") { value(hash) }
            jsonPath("$.tasks[0].files[0].uuid") { doesNotExist() }
            jsonPath("$.tasks[0].files[0].url") { value("/api/files/$hash") }
        }
        // 같은 내용의 다른 결과 행도 조회가 모호해지지 않아야 한다.
        val original = fileRepository.findAll().single()
        fileRepository.saveAndFlush(GeneratedFile(
            taskId = original.taskId, sha256 = original.sha256,
            storageKey = original.storageKey, metadata = original.metadata,
        ))
        mockMvc.get("/api/files/$hash") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `해시 자리에 UUID 나 잘못된 해시를 보내면 400 이다`() {
        val token = tokenOf("invalid-hash-owner")
        listOf(UUID.randomUUID().toString(), "A".repeat(64), "abc").forEach { invalid ->
            mockMvc.get("/api/files/$invalid") { header("Authorization", "Bearer $token") }
                .andExpect { status { isBadRequest() } }
        }
    }

    /** 주소를 테스트가 지어내지 않는다 — 응답이 준 것을 그대로 쓴다. */
    private fun fileUrlOf(jobUuid: String, token: String): String =
        query(jobUuid, token).andReturn().response.contentAsString
            .substringAfter("\"url\":\"").substringBefore("\"")

    companion object {
        private const val FAKE_MODEL = SharedTestConfig.FAKE_MODEL
        private const val FAKE_TEXT_MODEL = SharedTestConfig.FAKE_TEXT_MODEL
        private const val MIME = "image/png"
        private val OPTION =
            """{"type":"image-generate","prompt":"고양이","size":{"type":"ratio","ratio":"1:1","resolution":"1k"}}"""
    }
}
