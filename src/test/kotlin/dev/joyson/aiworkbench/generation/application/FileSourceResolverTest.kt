package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.generation.GeneratedFileTestRepository
import dev.joyson.aiworkbench.generation.GenerationJobTaskTestRepository
import dev.joyson.aiworkbench.generation.GenerationJobTestRepository
import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.generation.domain.GeneratedFileFinder
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.FileSource
import dev.joyson.aiworkbench.generation.domain.option.ImageOption
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import dev.joyson.aiworkbench.storage.Sha256
import dev.joyson.aiworkbench.upload.UploadedFiles
import org.springframework.beans.factory.annotation.Autowired
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 조회 화면에 붙이는 입력 파일 주소. 실행 시점의 준비(`resolve`)는 [TaskWorkerTest] 가 본다. */
class FileSourceResolverTest @Autowired constructor(
    private val jobRepository: GenerationJobTestRepository,
    private val taskRepository: GenerationJobTaskTestRepository,
    private val fileRepository: GeneratedFileTestRepository,
    private val generatedFileFinder: GeneratedFileFinder,
    private val uploadedFiles: UploadedFiles,
    private val fileStorage: FileStorage,
) : IntegrationTest() {

    private val issuer = PresignedUrlIssuer { key, name -> URI.create("https://storage.test/$key?name=$name") }

    private fun resolver(
        uploadedFiles: UploadedFiles = this.uploadedFiles,
        presignedUrlIssuer: PresignedUrlIssuer? = issuer,
    ) = FileSourceResolver(generatedFileFinder, uploadedFiles, fileStorage, presignedUrlIssuer)

    /** [workspaceUuid] 의 Job 이 만든 결과물 한 장. 보관소에는 쓰지 않는다 — 주소 발급은 바이트를 읽지 않는다. */
    private fun generatedIn(workspaceUuid: UUID): GeneratedFile {
        val job = jobRepository.saveAndFlush(
            GenerationJob(
                ownerUserUuid = workspaceUuid,
                workspaceUuid = workspaceUuid,
                option = ImageOption("고양이", ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K)),
                model = "fake-model",
                taskCount = 1,
            ),
        )
        val task = taskRepository.saveAndFlush(GenerationJobTask(jobId = job.id!!, sequence = 0))
        val sha256 = Sha256.of(UUID.randomUUID().toString().toByteArray()).hex
        return fileRepository.saveAndFlush(
            GeneratedFile(
                sha256 = sha256,
                taskId = task.id!!,
                storageKey = "users/$workspaceUuid/blobs/$sha256.png",
                metadata = FileMetadata(width = 1024, height = 1024, mimeType = "image/png", fileSize = 4),
            ),
        )
    }

    @Test
    fun `같은 서비스의 결과물은 서명 주소를 받고 남의 서비스 것은 빠진다`() {
        val workspace = UUID.randomUUID()
        val mine = generatedIn(workspace)
        val others = generatedIn(UUID.randomUUID())

        val urls = resolver().issuePreviewUrls(
            listOf(FileSource.Generated(mine.sha256), FileSource.Generated(others.sha256)),
            workspace,
        )

        assertEquals(
            mapOf<FileSource, URI>(
                FileSource.Generated(mine.sha256) to
                    URI.create("https://storage.test/${mine.storageKey}?name=${mine.sha256}.png"),
            ),
            urls,
        )
    }

    @Test
    fun `올린 파일은 upload 모듈에 한 번에 묻고 그 답을 그대로 쓴다`() {
        val workspace = UUID.randomUUID()
        val ready = Sha256.of("ready".toByteArray()).hex
        val missing = Sha256.of("missing".toByteArray()).hex
        val asked = mutableListOf<Collection<String>>()
        val uploads = object : UploadedFiles by uploadedFiles {
            override fun issueReadUrls(sha256s: Collection<String>, workspaceUuid: UUID): Map<String, URI> {
                asked += sha256s
                return mapOf(ready to URI.create("https://storage.test/upload/$ready"))
                    .takeIf { workspaceUuid == workspace }.orEmpty()
            }
        }

        val urls = resolver(uploadedFiles = uploads).issuePreviewUrls(
            listOf(FileSource.Uploaded(ready), FileSource.Uploaded(missing), FileSource.Uploaded(ready)),
            workspace,
        )

        assertEquals(mapOf<FileSource, URI>(FileSource.Uploaded(ready) to URI.create("https://storage.test/upload/$ready")), urls)
        assertEquals(listOf(setOf(ready, missing)), asked.map { it.toSet() }, "참조가 겹쳐도 한 번만 묻는다")
    }

    @Test
    fun `보관소가 서명할 수 없으면 결과물 주소는 비어 있다`() {
        val workspace = UUID.randomUUID()
        val mine = generatedIn(workspace)

        assertTrue(
            resolver(presignedUrlIssuer = null)
                .issuePreviewUrls(listOf(FileSource.Generated(mine.sha256)), workspace)
                .isEmpty(),
        )
    }
}
