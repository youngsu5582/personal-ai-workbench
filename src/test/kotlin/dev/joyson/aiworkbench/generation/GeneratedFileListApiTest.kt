package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.storage.Sha256
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import org.hamcrest.Matchers.hasKey
import org.hamcrest.Matchers.not
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.Test

/**
 * 라이브러리가 읽는 결과 파일 목록.
 *
 * 페이지가 작업이 아니라 파일 단위인지, 종류로 갈리는지, 다른 서비스의 파일이 섞이지 않는지를 고정한다.
 * 테스트 프로필은 워커가 꺼져 있어 결과 행을 직접 심는다.
 */
class GeneratedFileListApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val tokenService: TokenService,
    private val userRegistry: UserRegistry,
    private val jobRepository: GenerationJobTestRepository,
    private val taskRepository: GenerationJobTaskTestRepository,
    private val fileRepository: GeneratedFileTestRepository,
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

    private fun submit(token: String, option: String, model: String, taskCount: Int = 1, workspace: String? = null): String =
        mockMvc.post("/api/jobs") {
            header("Authorization", "Bearer $token")
            if (workspace != null) header("X-Workspace-Id", workspace)
            contentType = MediaType.APPLICATION_JSON
            content = """{"option":$option,"model":"$model","taskCount":$taskCount}"""
        }.andReturn().response.contentAsString
            .substringAfter("\"uuid\":\"").substringBefore("\"")

    /** Task 마다 결과 파일을 하나씩 심는다. 해시는 내용 대신 [seed] 로 짓는다 — 바이트를 읽지 않는 조회다. */
    private fun succeedWithFiles(jobUuid: String, seed: String, metadata: FileMetadata): List<String> {
        val job = jobRepository.findByUuid(UUID.fromString(jobUuid))!!
        return taskRepository.findAllByJobIdOrderBySequence(job.id!!).mapIndexed { i, task ->
            succeed(task)
            val sha256 = Sha256.of("$seed-$i".toByteArray()).hex
            fileRepository.save(GeneratedFile(sha256 = sha256, taskId = task.id!!, storageKey = "key-$sha256", metadata = metadata))
            sha256
        }
    }

    private fun succeed(task: GenerationJobTask) {
        task.transitionTo(TaskStatus.RUNNING)
        task.transitionTo(TaskStatus.SUCCEEDED)
        taskRepository.save(task)
    }

    private fun list(token: String, kind: String?, page: Int = 0, size: Int = 24, workspace: String? = null) =
        mockMvc.get("/api/files") {
            header("Authorization", "Bearer $token")
            if (workspace != null) header("X-Workspace-Id", workspace)
            if (kind != null) param("kind", kind)
            param("page", page.toString())
            param("size", size.toString())
        }

    @Test
    fun `결과 파일을 종류별로 최신순으로 파일 단위로 나누어 준다`() {
        val token = tokenOf("library-owner")
        val imageJob = submit(token, IMAGE_OPTION, SharedTestConfig.FAKE_MODEL, taskCount = 2)
        val (older, newer) = succeedWithFiles(imageJob, "image", IMAGE)
        val audioJob = submit(token, AUDIO_OPTION, SharedTestConfig.FAKE_AUDIO_MODEL)
        val (audio) = succeedWithFiles(audioJob, "audio", AUDIO)
        // 파일이 없는 작업은 목록에 자리를 차지하지 않는다 — 작업 단위로 나누면 여기서 빈 칸이 생긴다.
        submit(token, IMAGE_OPTION, SharedTestConfig.FAKE_MODEL)

        list(token, "image", page = 0, size = 1).andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[0].file.sha256") { value(newer) }
            jsonPath("$.items[0].file.url") { value("/api/files/$newer") }
            jsonPath("$.items[0].file.width") { value(1024) }
            jsonPath("$.items[0].createdAt") { isString() }
            jsonPath("$.items[0].job.uuid") { value(imageJob) }
            jsonPath("$.items[0].job.type") { value("image-generate") }
            jsonPath("$.items[0].job.model") { value(SharedTestConfig.FAKE_MODEL) }
            jsonPath("$.items[0].job.prompt") { value("고양이") }
            // 요청 전체는 싣지 않는다. 더 필요하면 작업 상세를 묻는다.
            jsonPath("$.items[0].job") { value(not(hasKey("option"))) }
            jsonPath("$.hasNext") { value(true) }
        }
        list(token, "image", page = 1, size = 1).andExpect {
            status { isOk() }
            jsonPath("$.items[0].file.sha256") { value(older) }
            jsonPath("$.hasNext") { value(false) }
        }
        list(token, "audio").andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[0].file.sha256") { value(audio) }
            jsonPath("$.items[0].file.durationSeconds") { value(8.48) }
            jsonPath("$.items[0].file") { value(not(hasKey("width"))) }
            // TTS 는 프롬프트 대신 읽은 글이 요청의 글이다.
            jsonPath("$.items[0].job.prompt") { value("안녕하세요") }
            jsonPath("$.hasNext") { value(false) }
        }
    }

    @Test
    fun `다른 서비스와 다른 사용자의 파일은 섞이지 않는다`() {
        val token = tokenOf("library-isolation-owner")
        val workspace = mockMvc.post("/api/services") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"별도 서비스"}"""
        }.andReturn().response.contentAsString.substringAfter("\"uuid\":\"").substringBefore("\"")
        val (mine) = succeedWithFiles(submit(token, IMAGE_OPTION, SharedTestConfig.FAKE_MODEL), "mine", IMAGE)
        val (other) = succeedWithFiles(submit(token, IMAGE_OPTION, SharedTestConfig.FAKE_MODEL, workspace = workspace), "other", IMAGE)
        val stranger = tokenOf("library-isolation-stranger")
        succeedWithFiles(submit(stranger, IMAGE_OPTION, SharedTestConfig.FAKE_MODEL), "stranger", IMAGE)

        list(token, "image").andExpect {
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[0].file.sha256") { value(mine) }
        }
        list(token, "image", workspace = workspace).andExpect {
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[0].file.sha256") { value(other) }
        }
    }

    @Test
    fun `종류와 페이지가 잘못되면 400 이고 토큰이 없으면 401 이다`() {
        val token = tokenOf("library-invalid")

        list(token, kind = null).andExpect { status { isBadRequest() } }
        list(token, kind = "text").andExpect { status { isBadRequest() } }
        list(token, kind = "IMAGE").andExpect { status { isBadRequest() } }
        list(token, kind = "image", page = -1).andExpect { status { isBadRequest() } }
        list(token, kind = "image", size = 0).andExpect { status { isBadRequest() } }
        list(token, kind = "image", size = 61).andExpect { status { isBadRequest() } }
        mockMvc.get("/api/files") { param("kind", "image") }.andExpect { status { isUnauthorized() } }
    }

    companion object {
        private const val IMAGE_OPTION =
            """{"type":"image-generate","prompt":"고양이","size":{"type":"ratio","ratio":"1:1","resolution":"1k"}}"""
        private const val AUDIO_OPTION =
            """{"type":"audio-generate","text":"안녕하세요","voice":"${SharedTestConfig.FAKE_VOICE}"}"""
        private val IMAGE = FileMetadata.Image(width = 1024, height = 1024, mimeType = "image/png", fileSize = 10)
        private val AUDIO = FileMetadata.Audio(durationSeconds = 8.48, mimeType = "audio/wav", fileSize = 10)
    }
}
