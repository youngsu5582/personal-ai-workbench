package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.storage.Sha256
import java.util.UUID
import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.generation.application.GenerationCommand
import dev.joyson.aiworkbench.generation.application.GenerationJobService
import dev.joyson.aiworkbench.generation.application.GenerationJobWriter
import dev.joyson.aiworkbench.generation.application.UnknownFileSourceException
import dev.joyson.aiworkbench.generation.application.UnsupportedModelException
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.FileSource
import dev.joyson.aiworkbench.generation.domain.option.ImageOption
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.ProviderRegistry
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFile
import dev.joyson.aiworkbench.upload.domain.UploadedFileMetadata
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 접수 여부를 판단하는 자리다. 쓰기가 잘 되는지는 [GenerationJobWriterTest] 가 본다.
 *
 * 앞으로 크레딧 차감·요금제 한도가 들어올 곳이라, 그 규칙들의 테스트도 여기 쌓인다.
 */
class GenerationJobServiceTest @Autowired constructor(
    private val service: GenerationJobService,
    private val jobRepository: GenerationJobTestRepository,
    private val taskRepository: GenerationJobTaskTestRepository,
    private val generatedFileRepository: GeneratedFileTestRepository,
    private val uploadedFileRepository: UploadedFileRepository,
    private val fileStorage: FileStorage,
) : IntegrationTest() {

    private fun command(model: String = MODEL, taskCount: Int = 2) = GenerationCommand(
        option = ImageOption("고양이", ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K)),
        model = model,
        taskCount = taskCount,
    )

    @Test
    fun `다룰 수 있는 모델이면 접수하고 Task 까지 만든다`() {
        val view = service.submit(ownerUuid = UUID.randomUUID(), command = command())

        assertEquals(1, jobRepository.count())
        assertEquals(2, taskRepository.count())
        assertEquals(2, view.progress.total)
    }

    /**
     * 컨트롤러가 아니라 여기서 거른다.
     * 호출 경로가 늘어나면 호출자마다 같은 검사를 기억해야 하고, 한 곳만 빠뜨려도 조용히 통과한다.
     */
    @Test
    fun `다룰 Provider 가 없는 모델은 접수되지 않는다`() {
        assertFailsWith<UnsupportedModelException> {
            service.submit(ownerUuid = UUID.randomUUID(), command = command(model = "존재하지-않는-모델"))
        }

        // 검증이 기록보다 먼저라 흔적이 남지 않는다.
        assertEquals(0, jobRepository.count())
        assertEquals(0, taskRepository.count())
    }

    /**
     * 참조가 잘못됐는데 접수하면, 몇 분 뒤 Task 실패로만 드러난다.
     * 그 실패는 결정적이라 재시도해도 같고 큐 자리만 태운다 — 공짜로 막을 수 있을 때 막는다.
     */
    @Test
    fun `고칠 이미지가 없으면 접수되지 않는다`() {
        assertFailsWith<UnknownFileSourceException> {
            service.submit(ownerUuid = UUID.randomUUID(), command = editCommand())
        }

        // 검증이 기록보다 먼저라 흔적이 남지 않는다.
        assertEquals(0, jobRepository.count())
        assertEquals(0, taskRepository.count())
    }

    @Test
    fun `내가 만든 이미지를 가리키면 접수된다`() {
        val me = UUID.randomUUID()

        val view = service.submit(ownerUuid = me, command = editCommand(FileSource.Generated(fileOwnedBy(me))))

        assertEquals(1, view.progress.total)
    }

    /** 남의 파일은 없는 것과 같은 실패다 — 구분하면 그 uuid 의 존재가 새어 나간다. */
    @Test
    fun `남의 이미지를 가리키면 없는 것과 같은 실패다`() {
        val theirFile = fileOwnedBy(UUID.randomUUID())

        assertFailsWith<UnknownFileSourceException> {
            service.submit(ownerUuid = UUID.randomUUID(), command = editCommand(FileSource.Generated(theirFile)))
        }
    }

    @Test
    fun `올린 이미지가 선언대로 올라왔으면 접수되고 판정이 남는다`() {
        val me = UUID.randomUUID()
        val upload = uploadOwnedBy(me, content = png)

        val view = service.submit(ownerUuid = me, command = editCommand(FileSource.Uploaded(upload.sha256)))

        assertEquals(1, view.progress.total)
        assertEquals(UploadStatus.READY, uploadedFileRepository.findByOwnerUserUuidAndSha256(me, upload.sha256)?.status)
    }

    @Test
    fun `올린 이미지가 선언과 다른 형식이면 접수되지 않는다`() {
        val me = UUID.randomUUID()
        // zip 을 png 라고 선언했다.
        val upload = uploadOwnedBy(me, content = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0))

        assertFailsWith<UnknownFileSourceException> {
            service.submit(ownerUuid = me, command = editCommand(FileSource.Uploaded(upload.sha256)))
        }
        assertEquals(0, jobRepository.count())
    }

    @Test
    fun `주소만 받고 올리지 않은 이미지는 접수되지 않는다`() {
        val me = UUID.randomUUID()
        val upload = uploadOwnedBy(me, content = null)

        assertFailsWith<UnknownFileSourceException> {
            service.submit(ownerUuid = me, command = editCommand(FileSource.Uploaded(upload.sha256)))
        }
    }

    @Test
    fun `남이 올린 이미지를 가리키면 없는 것과 같은 실패다`() {
        val upload = uploadOwnedBy(UUID.randomUUID(), content = png)

        assertFailsWith<UnknownFileSourceException> {
            service.submit(ownerUuid = UUID.randomUUID(), command = editCommand(FileSource.Uploaded(upload.sha256)))
        }
    }

    /** 발급만 된 업로드 한 개를 만들고, [content] 가 있으면 보관소에 올려 둔다. 선언은 png 다. */
    private fun uploadOwnedBy(ownerUuid: UUID, content: ByteArray?): UploadedFile {
        val uuid = Sha256.of(UUID.randomUUID().toString().toByteArray()).hex
        val file = uploadedFileRepository.save(
            UploadedFile(
                sha256 = uuid,
                ownerUserUuid = ownerUuid,
                storageKey = "users/$ownerUuid/uploads/$uuid",
                metadata = UploadedFileMetadata(mimeType = "image/png", fileSize = (content ?: png).size.toLong()),
            ),
        )
        content?.let { fileStorage.put(file.storageKey, it, "image/png") }
        return file
    }

    /** 누군가의 결과물 한 개를 만들어 둔다. 소유자는 Job 이 알고, 파일은 Task 를 거쳐 거기 닿는다. */
    private fun fileOwnedBy(ownerUuid: UUID): String {
        val job = jobRepository.saveAndFlush(
            GenerationJob(
                ownerUserUuid = ownerUuid,
                option = command().option,
                model = MODEL,
                taskCount = 1,
            ),
        )
        val task = taskRepository.saveAndFlush(GenerationJobTask(jobId = job.id!!, sequence = 0))
        return generatedFileRepository.saveAndFlush(
            GeneratedFile(
                sha256 = Sha256.of(UUID.randomUUID().toString().toByteArray()).hex,
                taskId = task.id!!,
                storageKey = "users/$ownerUuid/blobs/aa/bb/deadbeef.png",
                metadata = FileMetadata(width = 1024, height = 1024, mimeType = "image/png", fileSize = 10),
            ),
        ).sha256
    }

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13)

    private fun editCommand(source: FileSource = FileSource.Generated(Sha256.of(UUID.randomUUID().toString().toByteArray()).hex)) = GenerationCommand(
        option = ImageOption(
            prompt = "수채화로",
            sources = listOf(source),
            size = ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K),
        ),
        model = MODEL,
        taskCount = 1,
    )

    private companion object {
        const val MODEL = SharedTestConfig.FAKE_MODEL
    }
}
