package dev.joyson.aiworkbench.workspace

import com.jayway.jsonpath.JsonPath
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.generation.GenerationJobTaskTestRepository
import dev.joyson.aiworkbench.generation.GenerationJobTestRepository
import dev.joyson.aiworkbench.generation.GeneratedFileTestRepository
import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.PresignedUploadIssuer
import dev.joyson.aiworkbench.storage.Sha256
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import dev.joyson.aiworkbench.usage.ProviderCallRecorder
import dev.joyson.aiworkbench.usage.RecordProviderCallCommand
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import dev.joyson.aiworkbench.workspace.application.WorkspaceService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@Import(ServiceIsolationApiTest.SigningConfig::class)
class ServiceIsolationApiTest @Autowired constructor(
    private val mvc: MockMvc,
    private val users: UserRegistry,
    private val tokens: TokenService,
    private val registry: WorkspaceRegistry,
    private val workspaces: WorkspaceService,
    private val jobs: GenerationJobTestRepository,
    private val tasks: GenerationJobTaskTestRepository,
    private val files: GeneratedFileTestRepository,
    private val uploads: UploadedFileRepository,
    private val storage: FileStorage,
    private val usage: ProviderCallRecorder,
) : IntegrationTest() {
    @Test
    fun `같은 사용자의 서비스도 작업과 파일과 사용량이 격리된다`() {
        val user = users.resolveOrRegister(RegisterIdentityCommand.of("https://accounts.google.com", UUID.randomUUID().toString(), "사용자", null))
        val token = tokens.issueAccessToken(user.uuid, user.displayName).accessToken
        val a = assertNotNull(registry.findDefaultUuid(user.uuid))
        val b = workspaces.create(user.uuid, "서비스 B").uuid
        val stranger = users.resolveOrRegister(RegisterIdentityCommand.of("https://accounts.google.com", UUID.randomUUID().toString(), "다른 사용자", null))
        val foreign = assertNotNull(registry.findDefaultUuid(stranger.uuid))
        mvc.get("/api/usage") { header("Authorization", "Bearer $token"); header("X-Workspace-Id", foreign.toString()) }
            .andExpect { status { isNotFound() } }
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0)
        val hash = Sha256.of(png).hex
        val payload = """{"option":{"type":"text-generate","prompt":"안녕"},"model":"${SharedTestConfig.FAKE_TEXT_MODEL}","taskCount":1}"""
        fun postJob(scope: UUID?) = mvc.post("/api/jobs") {
            header("Authorization", "Bearer $token")
            if (scope != null) header("X-Workspace-Id", scope.toString())
            contentType = MediaType.APPLICATION_JSON
            content = payload
        }.andExpect { status { isAccepted() } }.andReturn().response.contentAsString
        val jobA: String = JsonPath.read(postJob(null), "$.uuid")
        val jobB: String = JsonPath.read(postJob(b), "$.uuid")
        fun getJob(uuid: String, scope: UUID?) = mvc.get("/api/jobs/$uuid") {
            header("Authorization", "Bearer $token")
            if (scope != null) header("X-Workspace-Id", scope.toString())
        }
        getJob(jobA, null).andExpect { status { isOk() } }
        getJob(jobA, b).andExpect { status { isNotFound() } }
        getJob(jobB, null).andExpect { status { isNotFound() } }
        getJob(jobB, b).andExpect { status { isOk() } }

        val job = assertNotNull(jobs.findByUuid(UUID.fromString(jobA)))
        val task = tasks.findAllByJobIdOrderBySequence(requireNotNull(job.id)).single()
        storage.put("legacy-path", png, "image/png")
        files.saveAndFlush(GeneratedFile(sha256 = hash, taskId = requireNotNull(task.id), storageKey = "legacy-path",
            metadata = FileMetadata(width = 1, height = 1, mimeType = "image/png", fileSize = png.size)))
        mvc.get("/api/files/$hash") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() } }
        mvc.get("/api/files/$hash") { header("Authorization", "Bearer $token"); header("X-Workspace-Id", b.toString()) }
            .andExpect { status { isNotFound() } }

        fun editWith(sourceType: String, scope: UUID?) = mvc.post("/api/jobs") {
            header("Authorization", "Bearer $token")
            if (scope != null) header("X-Workspace-Id", scope.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """{"option":{"type":"image-generate","prompt":"수정","sources":[{"type":"$sourceType","sha256":"$hash"}],"size":{"type":"ratio","ratio":"1:1","resolution":"1k"}},"model":"${SharedTestConfig.FAKE_MODEL}","taskCount":1}"""
        }
        editWith("generated", null).andExpect { status { isAccepted() } }
        editWith("generated", b).andExpect { status { isBadRequest() } }

        fun upload(scope: UUID?) = mvc.post("/api/uploads") {
            header("Authorization", "Bearer $token")
            if (scope != null) header("X-Workspace-Id", scope.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"image/png","contentLength":${png.size},"sha256":"$hash"}"""
        }.andExpect { status { isOk() } }
        upload(null)
        val uploadA = assertNotNull(uploads.findByWorkspaceUuidAndSha256(a, hash))
        storage.put(uploadA.storageKey, png, "image/png")
        editWith("uploaded", null).andExpect { status { isAccepted() } }
        editWith("uploaded", b).andExpect { status { isBadRequest() } }
        upload(b)
        val uploadB = assertNotNull(uploads.findByWorkspaceUuidAndSha256(b, hash))
        assertNotEquals(uploadA.storageKey, uploadB.storageKey)

        fun record(scope: UUID) = usage.record(RecordProviderCallCommand(taskUuid = UUID.randomUUID(), jobUuid = UUID.randomUUID(),
            ownerUserUuid = user.uuid, workspaceUuid = scope, provider = "fake", model = "model", succeeded = true, latencyMs = 1))
        record(a)
        record(b)
        usage.record(RecordProviderCallCommand(taskUuid = UUID.randomUUID(), jobUuid = UUID.randomUUID(),
            ownerUserUuid = stranger.uuid, workspaceUuid = foreign, provider = "fake", model = "model", succeeded = true, latencyMs = 1))
        mvc.get("/api/usage/total").andExpect { status { isUnauthorized() } }
        mvc.get("/api/usage") { header("Authorization", "Bearer $token") }.andExpect { jsonPath("$.calls") { value(1) } }
        mvc.get("/api/usage") { header("Authorization", "Bearer $token"); header("X-Workspace-Id", b.toString()) }
            .andExpect { jsonPath("$.calls") { value(1) } }
        mvc.get("/api/usage/total") { header("Authorization", "Bearer $token") }.andExpect { jsonPath("$.calls") { value(2) } }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SigningConfig {
        @Bean
        @Primary
        fun uploadSigner(): PresignedUploadIssuer = PresignedUploadIssuer { key, _, _, _ -> URI.create("https://storage.test/$key") }
    }
}
