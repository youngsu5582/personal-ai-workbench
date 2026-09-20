package dev.joyson.aiworkbench.generation

import java.util.UUID
import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.generation.application.GenerationCommand
import dev.joyson.aiworkbench.generation.application.GenerationJobSubmitter
import dev.joyson.aiworkbench.generation.application.GenerationJobWriter
import dev.joyson.aiworkbench.generation.application.UnknownImageSourceException
import dev.joyson.aiworkbench.generation.application.UnsupportedModelException
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.ImageSource
import dev.joyson.aiworkbench.generation.domain.option.ImageToImageOption
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.infrastructure.GeneratedFileRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.ProviderRegistry
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
class GenerationJobSubmitterTest @Autowired constructor(
    private val submitter: GenerationJobSubmitter,
    private val jobRepository: GenerationJobRepository,
    private val taskRepository: GenerationJobTaskRepository,
    private val generatedFileRepository: GeneratedFileRepository,
) : IntegrationTest() {

    private fun command(model: String = MODEL, taskCount: Int = 2) = GenerationCommand(
        option = TextToImageOption("고양이", ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K)),
        model = model,
        taskCount = taskCount,
    )

    @Test
    fun `다룰 수 있는 모델이면 접수하고 Task 까지 만든다`() {
        val view = submitter.submit(ownerUuid = UUID.randomUUID(), command = command())

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
            submitter.submit(ownerUuid = UUID.randomUUID(), command = command(model = "존재하지-않는-모델"))
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
        assertFailsWith<UnknownImageSourceException> {
            submitter.submit(ownerUuid = UUID.randomUUID(), command = editCommand())
        }

        // 검증이 기록보다 먼저라 흔적이 남지 않는다.
        assertEquals(0, jobRepository.count())
        assertEquals(0, taskRepository.count())
    }

    @Test
    fun `내가 만든 이미지를 가리키면 접수된다`() {
        val me = UUID.randomUUID()

        val view = submitter.submit(ownerUuid = me, command = editCommand(sourceUuid = fileOwnedBy(me)))

        assertEquals(1, view.progress.total)
    }

    /** 남의 파일은 없는 것과 같은 실패다 — 구분하면 그 uuid 의 존재가 새어 나간다. */
    @Test
    fun `남의 이미지를 가리키면 없는 것과 같은 실패다`() {
        val theirFile = fileOwnedBy(UUID.randomUUID())

        assertFailsWith<UnknownImageSourceException> {
            submitter.submit(ownerUuid = UUID.randomUUID(), command = editCommand(sourceUuid = theirFile))
        }
    }

    /** 누군가의 결과물 한 개를 만들어 둔다. 소유자는 Job 이 알고, 파일은 Task 를 거쳐 거기 닿는다. */
    private fun fileOwnedBy(ownerUuid: UUID): UUID {
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
                taskId = task.id!!,
                storageKey = "users/$ownerUuid/blobs/aa/bb/deadbeef.png",
                metadata = FileMetadata(width = 1024, height = 1024, mimeType = "image/png", fileSize = 10),
            ),
        ).uuid
    }

    private fun editCommand(sourceUuid: UUID = UUID.randomUUID()) = GenerationCommand(
        option = ImageToImageOption(
            prompt = "수채화로",
            sources = listOf(ImageSource.Generated(sourceUuid)),
            size = ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K),
        ),
        model = MODEL,
        taskCount = 1,
    )

    private companion object {
        const val MODEL = SharedTestConfig.FAKE_MODEL
    }
}
