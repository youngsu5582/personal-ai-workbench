package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.generation.infrastructure.GeneratedFileRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import dev.joyson.aiworkbench.generation.infrastructure.ProviderRequestFactory
import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResult
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.ImageMetadata
import dev.joyson.aiworkbench.provider.ProviderRegistry
import dev.joyson.aiworkbench.storage.FileStorage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 요청이 실제 결과물이 되기까지를 한 번에 본다 — 번역 → Provider → 보관 → 기록.
 *
 * Provider 와 보관소는 가짜다. 여기서 보려는 것은 **경로와 상태 전이**지 외부 API 의 동작이 아니다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(TaskStateWriter::class)
class TaskWorkerTest @Autowired constructor(
    private val stateWriter: TaskStateWriter,
    private val jobRepository: GenerationJobRepository,
    private val taskRepository: GenerationJobTaskRepository,
    private val generatedFileRepository: GeneratedFileRepository,
) {
    private val image = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    /** 키별로 마지막에 쓴 바이트만 남는다 — 실제 보관소의 덮어쓰기와 같다. */
    private val storage = object : FileStorage {
        val written = mutableMapOf<String, ByteArray>()
        override fun put(key: String, content: ByteArray, contentType: String) { written[key] = content }
        override fun read(key: String): ByteArray? = written[key]
    }

    private fun provider(behavior: () -> ExternalApiGenerateResponse) = object : ExternalApiProvider {
        override val name = "fake"
        override fun supports(model: String) = model == MODEL
        override fun generate(model: String, request: ExternalApiGenerateRequest) = behavior()
    }

    private fun success() = ExternalApiGenerateResponse(
        result = listOf(
            ExternalApiGenerateResult(
                image = image,
                metadata = ImageMetadata(1024, 1024, "image/png", image.size),
            ),
        ),
    )

    private fun workerWith(provider: ExternalApiProvider) = TaskWorker(
        taskRepository = taskRepository,
        jobRepository = jobRepository,
        providerRegistry = ProviderRegistry(listOf(provider)),
        requestFactory = ProviderRequestFactory(),
        fileStorage = storage,
        stateWriter = stateWriter,
    )

    private fun newTask(model: String = MODEL): Pair<GenerationJob, GenerationJobTask> {
        val job = jobRepository.saveAndFlush(
            GenerationJob(
                ownerUserId = 1L,
                option = TextToImageOption("고양이", ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K)),
                model = model,
                taskCount = 1,
            ),
        )
        val task = taskRepository.saveAndFlush(GenerationJobTask(jobId = job.id!!, sequence = 0))
        stateWriter.claim(limit = 1)
        return job to taskRepository.findById(task.id!!).get()
    }

    @Test
    fun `성공하면 파일이 보관되고 위치가 기록되고 Job 이 닫힌다`() {
        val (job, task) = newTask()

        workerWith(provider { success() }).process(task.id!!)

        val file = generatedFileRepository.findAllByTaskIdOrderBySequence(task.id!!).single()
        // 키에 작업 구조가 드러난다 — 나중에 접두사로 한 번에 지울 수 있다.
        assertEquals("jobs/${job.uuid}/tasks/${task.uuid}/0.png", file.storageKey)
        assertContentEquals(image, storage.read(file.storageKey))
        assertEquals(1024, file.metadata.width)
        assertEquals(image.size, file.metadata.fileSize)

        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(task.id!!).get().status)
        assertEquals(JobLifecycle.CLOSED, jobRepository.findById(job.id!!).get().status)
    }

    @Test
    fun `다시 해볼 만한 실패는 큐로 돌아가고 Job 은 열려 있다`() {
        val (job, task) = newTask()

        workerWith(provider { throw ExternalApiException(retryable = true, message = "429") })
            .process(task.id!!)

        assertEquals(TaskStatus.PENDING, taskRepository.findById(task.id!!).get().status)
        assertEquals(JobLifecycle.PENDING, jobRepository.findById(job.id!!).get().status)
        assertTrue(storage.written.isEmpty(), "실패했는데 파일이 남았다")
    }

    @Test
    fun `다시 해도 소용없는 실패는 그 자리에서 닫힌다`() {
        val (job, task) = newTask()

        workerWith(provider { throw ExternalApiException(retryable = false, message = "400 잘못된 요청") })
            .process(task.id!!)

        val failed = taskRepository.findById(task.id!!).get()
        assertEquals(TaskStatus.FAILED, failed.status)
        assertEquals("400 잘못된 요청", failed.failureReason)
        assertEquals(JobLifecycle.CLOSED, jobRepository.findById(job.id!!).get().status)
    }

    @Test
    fun `다룰 Provider 가 사라졌으면 재시도하지 않는다`() {
        // 접수 때는 있었지만 그 사이 설정이 바뀌어 없어질 수 있다.
        val (_, task) = newTask(model = "사라진-모델")

        workerWith(provider { success() }).process(task.id!!)

        assertEquals(TaskStatus.FAILED, taskRepository.findById(task.id!!).get().status)
    }

    @Test
    fun `한 번 실패해도 다시 집어 처리하면 결과가 하나 남는다`() {
        val (job, task) = newTask()
        var attempts = 0
        val worker = workerWith(
            provider {
                attempts += 1
                if (attempts == 1) throw ExternalApiException(retryable = true, message = "일시적 오류")
                success()
            },
        )

        worker.process(task.id!!)
        assertEquals(TaskStatus.PENDING, taskRepository.findById(task.id!!).get().status)

        // 폴러가 다시 집어 든다.
        stateWriter.claim(limit = 1)
        worker.process(task.id!!)

        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(task.id!!).get().status)
        assertEquals(JobLifecycle.CLOSED, jobRepository.findById(job.id!!).get().status)
        // 키가 Task 와 순번만으로 정해져 재시도가 잔해를 남기지 않는다.
        assertEquals(1, generatedFileRepository.findAllByTaskIdOrderBySequence(task.id!!).size)
        assertEquals(1, storage.written.size)
        assertEquals(2, taskRepository.findById(task.id!!).get().attemptCount)
    }

    private companion object {
        const val MODEL = "fake-image-1"
    }
}
