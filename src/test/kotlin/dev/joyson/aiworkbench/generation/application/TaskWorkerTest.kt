package dev.joyson.aiworkbench.generation.application

import java.util.UUID
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.generation.domain.Sha256
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.generation.infrastructure.GeneratedFileRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import dev.joyson.aiworkbench.generation.infrastructure.StorageKeys
import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.FailureKind
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResult
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.ImageMetadata
import dev.joyson.aiworkbench.provider.ProviderRegistry
import dev.joyson.aiworkbench.provider.ProviderUsage
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.usage.FailureKind as RecordedFailureKind
import dev.joyson.aiworkbench.usage.ImageRequest
import dev.joyson.aiworkbench.usage.ProviderCallRecorder
import dev.joyson.aiworkbench.usage.RecordProviderCallCommand
import dev.joyson.aiworkbench.usage.infrastructure.ProviderCallRepository
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 요청이 실제 결과물이 되기까지를 한 번에 본다 — 번역 → Provider → 지출 기록 → 보관 → 상태 기록.
 *
 * Provider 와 보관소는 가짜다. 여기서 보려는 것은 **경로와 상태 전이**지 외부 API 의 동작이 아니다.
 */
class TaskWorkerTest @Autowired constructor(
    private val stateWriter: TaskStateWriter,
    private val jobRepository: GenerationJobRepository,
    private val taskRepository: GenerationJobTaskRepository,
    private val generatedFileRepository: GeneratedFileRepository,
    private val callRecorder: ProviderCallRecorder,
    private val callRepository: ProviderCallRepository,
) : IntegrationTest() {
    private val image = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    /** 키별로 마지막에 쓴 바이트만 남는다 — 실제 보관소의 덮어쓰기와 같다. */
    private val storage = object : FileStorage {
        val written = mutableMapOf<String, ByteArray>()
        override fun put(key: String, content: ByteArray, contentType: String) { written[key] = content }
        override fun read(key: String): ByteArray? = written[key]
    }

    private fun provider(behavior: () -> ExternalApiGenerateResponse) = object : ExternalApiProvider {
        override val name = PROVIDER
        override fun supports(model: String) = model == MODEL
        override fun generate(model: String, request: ExternalApiGenerateRequest) = behavior()
    }

    private fun success(usage: ProviderUsage? = null, revisedPrompt: String? = null) =
        ExternalApiGenerateResponse(
            usage = usage,
            result = listOf(
                ExternalApiGenerateResult(
                    image = image,
                    metadata = ImageMetadata(1024, 1024, "image/png", image.size),
                    revisedPrompt = revisedPrompt,
                ),
            ),
        )

    private fun workerWith(
        provider: ExternalApiProvider,
        fileStorage: FileStorage = storage,
        recorder: ProviderCallRecorder = callRecorder,
    ) = TaskWorker(
        taskRepository = taskRepository,
        jobRepository = jobRepository,
        providerRegistry = ProviderRegistry(listOf(provider)),
        fileStorage = fileStorage,
        stateWriter = stateWriter,
        callRecorder = recorder,
    )

    private fun newTask(
        model: String = MODEL,
        size: ImageSize = ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K),
    ): Pair<GenerationJob, GenerationJobTask> {
        val job = jobRepository.saveAndFlush(
            GenerationJob(
                ownerUserUuid = UUID.randomUUID(),
                option = TextToImageOption("고양이", size),
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

        workerWith(provider { throw ExternalApiException(FailureKind.THROTTLED, "429") })
            .process(task.id!!)

        assertEquals(TaskStatus.PENDING, taskRepository.findById(task.id!!).get().status)
        assertEquals(JobLifecycle.PENDING, jobRepository.findById(job.id!!).get().status)
        assertTrue(storage.written.isEmpty(), "실패했는데 파일이 남았다")
    }

    @Test
    fun `다시 해도 소용없는 실패는 그 자리에서 닫힌다`() {
        val (job, task) = newTask()

        workerWith(provider { throw ExternalApiException(FailureKind.REJECTED, "400 잘못된 요청") })
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
                if (attempts == 1) throw ExternalApiException(FailureKind.PROVIDER_ERROR, "일시적 오류")
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
    fun `호출 한 번이 지출 한 줄로 남는다`() {
        val (job, task) = newTask()
        val usage = ProviderUsage(raw = mapOf("total_tokens" to 4200))

        workerWith(provider { success(usage) }).process(task.id!!)

        val call = callRepository.findAllByTaskUuid(task.uuid).single()
        assertTrue(call.succeeded)
        assertEquals(PROVIDER, call.provider)
        assertEquals(MODEL, call.model)
        assertEquals(job.uuid, call.jobUuid)
        // 소유자를 여기 복사해 둬야 지출 조회가 Job 을 조인하지 않는다.
        assertEquals(job.ownerUserUuid, call.ownerUserUuid)
        assertNotNull(call.usageRaw)

        // 단가표가 아직 없다. 모르는 것을 0 으로 적지 않는다.
        assertNull(call.cost)
    }

    /**
     * 지출 가시성의 핵심이다. 타임아웃은 저쪽에서 이미 만들어 과금한 뒤 우리만 못 받은 경우가 있어
     * **가장 설명이 필요한 지출**인데, 성공 경로에만 기록을 붙이면 이것이 통째로 사라진다.
     */
    @Test
    fun `실패한 호출도 지출로 남는다`() {
        val (_, task) = newTask()

        workerWith(provider { throw ExternalApiException(FailureKind.NO_RESPONSE, "[openai 응답 없음] Read timed out") })
            .process(task.id!!)

        val call = callRepository.findAllByTaskUuid(task.uuid).single()
        assertFalse(call.succeeded)
        assertEquals("[openai 응답 없음] Read timed out", call.failureReason)
        // 사유 문자열이 아니라 **이 값**으로 나중에 과금 여부를 판정한다.
        assertEquals(RecordedFailureKind.NO_RESPONSE, call.failureKind)
        assertNull(call.usageRaw, "실패했는데 사용량이 있다")
    }

    /**
     * 재시도마다 따로 과금되므로 행도 따로 쌓여야 한다.
     * 한 Task 에 한 행으로 접으면 합계가 실제 청구서보다 적어진다.
     */
    @Test
    fun `재시도하면 지출이 시도마다 쌓인다`() {
        val (_, task) = newTask()
        var attempts = 0
        val worker = workerWith(
            provider {
                attempts += 1
                if (attempts == 1) throw ExternalApiException(FailureKind.PROVIDER_ERROR, "일시적 오류")
                success()
            },
        )

        worker.process(task.id!!)
        stateWriter.claim(limit = 1)
        worker.process(task.id!!)

        val calls = callRepository.findAllByTaskUuid(task.uuid)
        assertEquals(2, calls.size, "재시도가 앞 시도의 지출을 덮었다")
        assertEquals(listOf(false, true), calls.map { it.succeeded })
    }

    /**
     * 과금이 걸리는 축은 요청에 적힌 `16:9 @ 2k` 가 아니라 그것을 푼 픽셀이다.
     * 그 값은 Job 어디에도 없으므로 이 행이 갖지 않으면 나중에 되살릴 수 없다.
     */
    @Test
    fun `지출에는 실제로 보낸 픽셀이 남는다`() {
        val (_, task) = newTask(size = ImageSize.ByRatio(AspectRatio.SIXTEEN_NINE, Resolution.TWO_K))

        workerWith(provider { success() }).process(task.id!!)

        val call = callRepository.findAllByTaskUuid(task.uuid).single()
        val request = assertIs<ImageRequest>(call.request)
        assertEquals(2048, request.width)
        assertEquals(1152, request.height)
        assertEquals("auto", request.quality)
    }

    /**
     * 다른 테스트의 Provider 는 즉시 답해서 지연이 늘 0 이다 —
     * 그래서 **항상 0 을 적는 버그가 있어도 전부 통과한다.** 이 테스트만 그것을 막는다.
     */
    @Test
    fun `지연은 Provider 가 답하기까지 걸린 시간이다`() {
        val (_, task) = newTask()

        workerWith(provider { Thread.sleep(SLOW_MS); success() }).process(task.id!!)

        val call = callRepository.findAllByTaskUuid(task.uuid).single()
        assertTrue(call.latencyMs >= THRESHOLD_MS, "지연을 재지 않는다: ${call.latencyMs}ms")
    }

    /**
     * 측정 구간을 Provider 호출로 좁혀 둔 이유를 고정한다.
     *
     * 보관 지연이 섞이면 이 숫자로 Provider 를 비교할 수 없다 —
     * 느려진 것이 저쪽인지 우리 보관소인지 구분되지 않는다.
     */
    @Test
    fun `지연에 보관 시간은 섞이지 않는다`() {
        val (_, task) = newTask()
        val slowStorage = object : FileStorage {
            override fun put(key: String, content: ByteArray, contentType: String) = Thread.sleep(SLOW_MS)
            override fun read(key: String): ByteArray? = null
        }

        workerWith(provider { success() }, fileStorage = slowStorage).process(task.id!!)

        val call = callRepository.findAllByTaskUuid(task.uuid).single()
        assertTrue(call.latencyMs < THRESHOLD_MS, "보관 시간이 Provider 지연에 섞였다: ${call.latencyMs}ms")
    }


    /**
     * 기록이 실패하면 그 호출의 토큰 수는 **영영 사라진다** — 응답은 한 번뿐이다.
     * 그래서 실패 로그가 마지막 사본이 되고, 거기에 원자료가 없으면 아무것도 복원할 수 없다.
     */
    @Test
    fun `기록이 실패해도 Task 는 죽지 않고 원자료는 로그로 남는다`() {
        val (_, task) = newTask()
        val raw = mapOf("total_tokens" to 210, "output_tokens" to 196)
        var seen: RecordProviderCallCommand? = null
        val brokenRecorder = object : ProviderCallRecorder {
            override fun record(command: RecordProviderCallCommand): UUID {
                seen = command
                throw IllegalStateException("원장에 적지 못했다")
            }
        }

        workerWith(provider { success(ProviderUsage(raw = raw)) }, recorder = brokenRecorder)
            .process(task.id!!)

        // 돈은 이미 나갔다. 여기서 Task 를 실패시키면 재시도가 또 쓴다.
        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(task.id!!).get().status)
        // 로그에 실릴 커맨드가 원자료를 들고 있어야 한다. toString 이 그것을 내보낸다.
        assertEquals(raw, assertNotNull(seen).usageRaw)
        assertTrue(seen.toString().contains("total_tokens"), "마지막 사본에 토큰이 없다")
    }


    /**
     * 종류가 사유 문자열이 아니라 **값으로** 흘러가는지 본다.
     *
     * 이게 끊기면 Phase 2 가 과금 여부를 판정할 근거를 잃고, 다시 메시지를 파싱하게 된다.
     */
    @Test
    fun `실패 종류가 기록까지 흘러간다`() {
        val cases = listOf(
            FailureKind.THROTTLED to RecordedFailureKind.THROTTLED,
            FailureKind.NO_RESPONSE to RecordedFailureKind.NO_RESPONSE,
            FailureKind.UNKNOWN to RecordedFailureKind.UNKNOWN,
        )

        cases.forEach { (thrown, expected) ->
            val (_, task) = newTask()
            workerWith(provider { throw ExternalApiException(thrown, "사유") }).process(task.id!!)

            assertEquals(expected, callRepository.findAllByTaskUuid(task.uuid).single().failureKind)
        }
    }

    /**
     * 포트에서 저장까지 값이 끊기지 않는지 본다.
     *
     * 우리가 보낸 문장이 아니라 **이것이 실제 입력**이라, 끊기면 결과가 기대와 다른 이유를
     * 영영 설명할 수 없다.
     */
    @Test
    fun `모델이 다시 쓴 프롬프트가 저장까지 닿는다`() {
        val (_, task) = newTask()
        val revised = "창밖을 보는 고양이, 오후의 부드러운 빛, 얕은 피사계 심도"

        workerWith(provider { success(revisedPrompt = revised) }).process(task.id!!)

        val file = generatedFileRepository.findAllByTaskId(task.id!!).single()
        assertEquals(revised, assertNotNull(file.providerInfo).revisedPrompt)
    }

    /** 안 주는 Provider 도 있다. 없다고 저장이 깨지면 안 된다. */
    @Test
    fun `다시 쓴 프롬프트가 없어도 저장된다`() {
        val (_, task) = newTask()

        workerWith(provider { success() }).process(task.id!!)

        // 아무것도 안 알려주면 칸 자체가 비어 있다 — 빈 객체를 남기지 않는다.
        assertNull(generatedFileRepository.findAllByTaskId(task.id!!).single().providerInfo)
    }

    private companion object {
        const val MODEL = "fake-image-1"
        const val PROVIDER = "fake"

        /**
         * 재는지 안 재는지만 가리면 되므로 짧게 둔다. 다만 둘의 간격은 넉넉히 벌린다 —
         * 붙여 두면 CI 가 느리거나 GC 가 끼었을 때 간헐적으로 실패한다.
         */
        const val SLOW_MS = 200L
        const val THRESHOLD_MS = 100
    }
}
