package dev.joyson.aiworkbench.generation.application

import java.util.UUID
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.generation.domain.Sha256
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.ImageSource
import dev.joyson.aiworkbench.generation.domain.option.ImageToImageOption
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.generation.infrastructure.GeneratedFileRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import dev.joyson.aiworkbench.generation.infrastructure.ImageSourceFinder
import dev.joyson.aiworkbench.generation.infrastructure.StorageKeys
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
class TaskWorkerTest @Autowired constructor(
    private val stateWriter: TaskStateWriter,
    private val jobRepository: GenerationJobRepository,
    private val taskRepository: GenerationJobTaskRepository,
    private val generatedFileRepository: GeneratedFileRepository,
    private val imageSourceFinder: ImageSourceFinder,
) : IntegrationTest() {
    private val image = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    /** 키별로 마지막에 쓴 바이트만 남는다 — 실제 보관소의 덮어쓰기와 같다. */
    private val storage = object : FileStorage {
        val written = mutableMapOf<String, ByteArray>()

        /** 입력 이미지를 몇 번 읽었는지. 재시도 비용을 사실로 붙잡아 두는 데 쓴다. */
        var reads = 0
        override fun put(key: String, content: ByteArray, contentType: String) { written[key] = content }
        override fun read(key: String): ByteArray? {
            reads += 1
            return written[key]
        }
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
        // 입력과 결과가 같은 보관소에서 오가야 "만든 것을 다시 고친다" 가 성립한다.
        imageSourceResolver = ImageSourceResolver(imageSourceFinder, storage),
        fileStorage = storage,
        stateWriter = stateWriter,
    )

    private fun newTask(
        model: String = MODEL,
        option: GenerationOption = TextToImageOption("고양이", ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K)),
        ownerUuid: UUID = UUID.randomUUID(),
    ): Pair<GenerationJob, GenerationJobTask> {
        val job = jobRepository.saveAndFlush(
            GenerationJob(
                ownerUserUuid = ownerUuid,
                option = option,
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

        val file = generatedFileRepository.findAllByTaskId(task.id!!).single()
        // 자리는 내용이 정하고, 소유자로 먼저 갈린다.
        assertEquals(
            StorageKeys.generatedFile(job.ownerUserUuid, Sha256.of(image), "image/png"),
            file.storageKey,
        )
        assertContentEquals(image, storage.read(file.storageKey))
        assertEquals(1024, file.metadata.width)
        assertEquals(image.size, file.metadata.fileSize)

        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(task.id!!).get().status)
        assertEquals(JobLifecycle.CLOSED, jobRepository.findById(job.id!!).get().status)
    }

    @Test
    fun `같은 바이트를 두 장 내면 한 자리에 보관되고 행은 둘이다`() {
        val (_, task) = newTask()

        workerWith(provider { twoResults(image, image) }).process(task.id!!)

        val files = generatedFileRepository.findAllByTaskId(task.id!!)
        // 내용이 같으면 자리도 같다 — 이것이 내용 주소로 옮겨왔다는 증거다.
        assertEquals(1, storage.written.size)
        assertEquals(1, files.map { it.storageKey }.toSet().size)
        // 행은 따로 남는다. 키를 공유해도 각자 자기 식별자를 갖는다.
        assertEquals(2, files.size)
        assertEquals(2, files.map { it.uuid }.toSet().size)
    }

    @Test
    fun `다른 바이트는 다른 자리에 보관된다`() {
        val (_, task) = newTask()

        workerWith(provider { twoResults(image, byteArrayOf(9, 9, 9)) }).process(task.id!!)

        val files = generatedFileRepository.findAllByTaskId(task.id!!)
        assertEquals(2, files.map { it.storageKey }.toSet().size)
        assertEquals(2, storage.written.size)
    }

    @Test
    fun `보관소 키는 소유자로 먼저 갈린다`() {
        val (job, task) = newTask()

        workerWith(provider { success() }).process(task.id!!)

        val file = generatedFileRepository.findAllByTaskId(task.id!!).single()
        assertTrue(file.storageKey.startsWith("users/${job.ownerUserUuid}/blobs/"))
    }

    private fun twoResults(first: ByteArray, second: ByteArray) = ExternalApiGenerateResponse(
        result = listOf(first, second).map {
            ExternalApiGenerateResult(
                image = it,
                metadata = ImageMetadata(1024, 1024, "image/png", it.size),
            )
        },
    )

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
        // 재시도가 같은 바이트를 내면 자리도 같다 — 이 테스트의 가짜 Provider 가 그렇다.
        assertEquals(1, generatedFileRepository.findAllByTaskId(task.id!!).size)
        assertEquals(1, storage.written.size)
        assertEquals(2, taskRepository.findById(task.id!!).get().attemptCount)
    }

    @Test
    fun `참조한 이미지를 읽어 Provider 에 넘긴다`() {
        // 입력은 우리가 만든 것뿐이라, 먼저 글에서 하나 만든다.
        val (first, firstTask) = newTask()
        workerWith(provider { success() }).process(firstTask.id!!)
        val source = generatedFileRepository.findAllByTaskId(firstTask.id!!).single()

        val (_, editTask) = newTask(option = editOption(source.uuid), ownerUuid = first.ownerUserUuid)
        var captured: ExternalApiGenerateRequest? = null

        workerWith(
            object : ExternalApiProvider {
                override val name = "fake"
                override fun supports(model: String) = model == MODEL
                override fun generate(model: String, request: ExternalApiGenerateRequest): ExternalApiGenerateResponse {
                    captured = request
                    return success()
                }
            },
        ).process(editTask.id!!)

        val images = captured!!.images
        assertEquals(1, images.size)
        assertContentEquals(image, images[0].bytes, "보관소에서 읽은 바이트가 그대로 실려야 한다")
        assertEquals("image/png", images[0].mimeType)
        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(editTask.id!!).get().status)
    }

    @Test
    fun `참조한 이미지가 없으면 재시도하지 않는다`() {
        // 접수 때는 있었지만 그 사이 지워졌을 수 있다. 다시 읽어도 없으므로 그 자리에서 닫는다.
        val (_, task) = newTask(option = editOption(UUID.randomUUID()))

        workerWith(provider { success() }).process(task.id!!)

        assertEquals(TaskStatus.FAILED, taskRepository.findById(task.id!!).get().status)
        assertTrue(storage.written.isEmpty(), "입력을 못 읽었는데 결과가 남았다")
    }

    /** 바이트를 워커가 들고 있지 않다는 사실. 캐시를 넣으면 이 값이 1이 된다. */
    @Test
    fun `재시도하면 입력 이미지를 다시 읽는다`() {
        val (first, firstTask) = newTask()
        workerWith(provider { success() }).process(firstTask.id!!)
        val source = generatedFileRepository.findAllByTaskId(firstTask.id!!).single()

        val (_, editTask) = newTask(option = editOption(source.uuid), ownerUuid = first.ownerUserUuid)
        var attempts = 0
        val worker = workerWith(
            provider {
                attempts += 1
                if (attempts == 1) throw ExternalApiException(retryable = true, message = "일시적 오류")
                success()
            },
        )

        val before = storage.reads
        worker.process(editTask.id!!)
        stateWriter.claim(limit = 1)
        worker.process(editTask.id!!)

        assertEquals(2, storage.reads - before)
    }

    private fun editOption(sourceUuid: UUID) = ImageToImageOption(
        prompt = "수채화로",
        sources = listOf(ImageSource.Generated(sourceUuid)),
        size = ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K),
    )

    private companion object {
        const val MODEL = "fake-image-1"
    }
}
