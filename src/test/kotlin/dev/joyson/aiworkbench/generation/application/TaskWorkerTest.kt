package dev.joyson.aiworkbench.generation.application

import java.util.UUID
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobRepository
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.GenerationJobTaskRepository
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.storage.Sha256
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.FileSource
import dev.joyson.aiworkbench.generation.domain.option.ImageOption
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.GeneratedFileTestRepository
import dev.joyson.aiworkbench.generation.GenerationJobTestRepository
import dev.joyson.aiworkbench.generation.GenerationJobTaskTestRepository
import dev.joyson.aiworkbench.generation.domain.GeneratedFileFinder
import dev.joyson.aiworkbench.storage.BlobKey
import dev.joyson.aiworkbench.provider.AppliedParameters
import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.FailureKind
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiFileInput
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import java.net.URI
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResult
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.ExternalApiTextProvider
import dev.joyson.aiworkbench.provider.ExternalApiTextRequest
import dev.joyson.aiworkbench.provider.ExternalApiTextResponse
import dev.joyson.aiworkbench.provider.StopReason
import dev.joyson.aiworkbench.generation.domain.GeneratedTextRepository
import dev.joyson.aiworkbench.generation.domain.option.Effort
import dev.joyson.aiworkbench.generation.domain.option.TextFormat
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption
import dev.joyson.aiworkbench.usage.TextRequest
import dev.joyson.aiworkbench.provider.ImageMetadata
import dev.joyson.aiworkbench.provider.ProviderRegistry
import dev.joyson.aiworkbench.provider.ProviderUsage
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.FileStorageException
import dev.joyson.aiworkbench.usage.FailureKind as RecordedFailureKind
import dev.joyson.aiworkbench.usage.ImageRequest
import dev.joyson.aiworkbench.upload.UploadedFiles
import dev.joyson.aiworkbench.upload.UsableUpload
import dev.joyson.aiworkbench.usage.ProviderCallRecorder
import dev.joyson.aiworkbench.usage.RecordProviderCallCommand
import dev.joyson.aiworkbench.usage.ProviderCallTestRepository
import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.core.instrument.MeterRegistry
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
    private val jobRepository: GenerationJobTestRepository,
    private val taskRepository: GenerationJobTaskTestRepository,
    private val generatedFileRepository: GeneratedFileTestRepository,
    /** 워커가 받는 것. 확인은 전체 CRUD 가 있는 [jobRepository]·[taskRepository] 로 한다. */
    private val jobPort: GenerationJobRepository,
    private val taskPort: GenerationJobTaskRepository,
    private val callRecorder: ProviderCallRecorder,
    private val callRepository: ProviderCallTestRepository,
    private val generatedFileFinder: GeneratedFileFinder,
    private val uploadedFiles: UploadedFiles,
    private val meterRegistry: MeterRegistry,
    private val generatedTextRepository: GeneratedTextRepository,
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
        override fun readRange(key: String, range: LongRange) = error("일부만 읽지 않는다")
        override fun delete(key: String) = error("지우지 않는다")
    }

    private fun provider(behavior: () -> ExternalApiGenerateResponse) = object : ExternalApiProvider {
        override val name = PROVIDER
        override fun supports(model: String) = model == MODEL
        override fun generate(model: String, request: ExternalApiGenerateRequest) = behavior()
    }

    private fun success(
        usage: ProviderUsage? = null,
        revisedPrompt: String? = null,
        applied: AppliedParameters? = null,
        providerId: String? = null,
    ) = ExternalApiGenerateResponse(
        usage = usage,
        applied = applied,
        result = listOf(
            ExternalApiGenerateResult(
                image = image,
                metadata = ImageMetadata(1024, 1024, "image/png", image.size),
                revisedPrompt = revisedPrompt,
                providerId = providerId,
            ),
        ),
    )

    private fun workerWith(
        provider: ExternalApiProvider,
        fileStorage: FileStorage = storage,
        recorder: ProviderCallRecorder = callRecorder,
        presignedUrlIssuer: PresignedUrlIssuer? = null,
        uploadedFiles: UploadedFiles = this.uploadedFiles,
        textProvider: ExternalApiTextProvider? = null,
    ) = TaskWorker(
        taskRepository = taskPort,
        jobRepository = jobPort,
        providerRegistry = ProviderRegistry(listOf(provider), listOfNotNull(textProvider)),
        // 입력과 결과가 같은 보관소에서 오가야 "만든 것을 다시 고친다" 가 성립한다.
        fileSourceResolver = FileSourceResolver(generatedFileFinder, uploadedFiles, fileStorage, presignedUrlIssuer),
        fileStorage = fileStorage,
        stateWriter = stateWriter,
        callRecorder = recorder,
        meterRegistry = meterRegistry,
    )

    private fun newTask(
        model: String = MODEL,
        size: ImageSize = ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K),
        option: GenerationOption = ImageOption("고양이", size),
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
            BlobKey.of(job.ownerUserUuid, Sha256.of(image), "image/png").value,
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
        assertEquals(2, files.map { it.id }.toSet().size)
        assertEquals(1, files.map { it.sha256 }.toSet().size)
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

    /**
     * 사유는 길이를 Provider 가 정한다. 사유 칸을 넘겨 상태 기록이 실패하면 Task 가 RUNNING 에 남는다 —
     * 실패로 닫히는 것보다 나쁘다.
     */
    @Test
    fun `긴 실패 사유도 Task 를 닫는다`() {
        val (job, task) = newTask()
        val long = "[openai 400 BAD_REQUEST] " + "Invalid value for 'size': must be a multiple of 16. ".repeat(6)

        workerWith(provider { throw ExternalApiException(FailureKind.REJECTED, long) }).process(task.id!!)

        val failed = taskRepository.findById(task.id!!).get()
        assertEquals(TaskStatus.FAILED, failed.status)
        assertTrue(long.startsWith(failed.failureReason!!), "사유의 앞부분이 남아야 한다: ${failed.failureReason}")
        assertEquals(JobLifecycle.CLOSED, jobRepository.findById(job.id!!).get().status)
    }

    /**
     * 끝난 호출만 재면 멈춘 호출은 타임아웃이 나야 보인다. 도는 동안 "진행 중" 에 있어야 그 전에 안다.
     * 끝나면 반드시 빠져야 한다 — 남으면 끝나지 않은 호출처럼 나이가 계속 는다.
     */
    @Test
    fun `Provider 호출은 도는 동안만 진행 중으로 센다`() {
        val (_, task) = newTask()
        var duringCall = -1

        workerWith(provider {
            duringCall = activeCalls().activeTasks()
            success()
        }).process(task.id!!)

        assertEquals(1, duringCall)
        assertEquals(0, activeCalls().activeTasks())
    }

    @Test
    fun `Provider 가 실패해도 진행 중에서 빠진다`() {
        val (_, task) = newTask()

        workerWith(provider { throw ExternalApiException(FailureKind.NO_RESPONSE, "읽기 타임아웃") })
            .process(task.id!!)

        assertEquals(0, activeCalls().activeTasks())
    }

    private fun activeCalls(): LongTaskTimer =
        meterRegistry.get("workbench.provider.call.active").tags("provider", PROVIDER, "model", MODEL).longTaskTimer()

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
            override fun readRange(key: String, range: LongRange) = error("일부만 읽지 않는다")
            override fun delete(key: String) = error("지우지 않는다")
        }

        workerWith(provider { success() }, fileStorage = slowStorage).process(task.id!!)

        val call = callRepository.findAllByTaskUuid(task.uuid).single()
        assertTrue(call.latencyMs < THRESHOLD_MS, "보관 시간이 Provider 지연에 섞였다: ${call.latencyMs}ms")
    }

    /**
     * 보관 시간은 Provider 지연과 따로 잰다. 느려진 것이 저쪽인지 우리 보관소인지 가르려면 둘이 다른 지표여야 한다.
     * 실패도 센다 — 보관에 실패한 Task 는 다시 큐로 가므로, 이 수가 없으면 재시도가 어디서 오는지 모른다.
     */
    @Test
    fun `보관소에 쓴 시간을 결과별로 잰다`() {
        // 컨텍스트를 공유하므로 다른 테스트가 센 것이 남아 있다. 늘어난 만큼만 본다.
        val writtenBefore = storageWrites("success")
        val failedBefore = storageWrites("failure")
        val brokenStorage = object : FileStorage {
            override fun put(key: String, content: ByteArray, contentType: String) =
                throw FileStorageException(retryable = true, message = "보관소가 응답하지 않는다")
            override fun read(key: String): ByteArray? = null
            override fun readRange(key: String, range: LongRange) = error("일부만 읽지 않는다")
            override fun delete(key: String) = error("지우지 않는다")
        }

        val (_, stored) = newTask()
        workerWith(provider { success() }).process(stored.id!!)
        val (_, broken) = newTask()
        workerWith(provider { success() }, fileStorage = brokenStorage).process(broken.id!!)

        assertEquals(1, storageWrites("success") - writtenBefore)
        assertEquals(1, storageWrites("failure") - failedBefore)
        assertEquals(TaskStatus.PENDING, taskRepository.findById(broken.id!!).get().status)
    }

    private fun storageWrites(outcome: String): Long =
        meterRegistry.get("workbench.storage.write").tag("outcome", outcome).timer().count()

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

    /**
     * 이 기능의 주장은 **과금이 실제로 쓰인 값을 따른다** 는 것이다.
     * 어댑터가 실제 값을 알아내도 원장까지 안 닿으면 그 주장이 성립하지 않는다.
     */
    @Test
    fun `저쪽이 실제로 쓴 값이 지출 원장까지 닿는다`() {
        // 16:9 @2k 를 요청하지만 저쪽은 1024x1024 로 만들었다고 답한다.
        val (_, task) = newTask(size = ImageSize.ByRatio(AspectRatio.SIXTEEN_NINE, Resolution.TWO_K))
        val applied = AppliedParameters(
            width = 1024,
            height = 1024,
            quality = "low",
            background = "opaque",
            outputFormat = "png",
        )

        workerWith(provider { success(applied = applied) }).process(task.id!!)

        val request = assertIs<ImageRequest>(callRepository.findAllByTaskUuid(task.uuid).single().request)
        assertEquals(1024, request.width, "요청한 2048 이 아니라 실제 만들어진 값이어야 한다")
        assertEquals("low", request.quality)
        // 우리가 보내지 않은 값도 남는다 — 단가 축인지 아직 모른다.
        assertEquals("opaque", request.background)
    }

    /** 안 답해주는 Provider 도 있다. 그때는 보낸 값이 우리가 가진 전부다. */
    @Test
    fun `저쪽이 말하지 않으면 보낸 값이 남는다`() {
        val (_, task) = newTask(size = ImageSize.ByRatio(AspectRatio.SIXTEEN_NINE, Resolution.TWO_K))

        workerWith(provider { success() }).process(task.id!!)

        val request = assertIs<ImageRequest>(callRepository.findAllByTaskUuid(task.uuid).single().request)
        assertEquals(2048, request.width)
        assertNull(request.background)
    }

    /**
     * 저쪽이 일부만 답하는 경우다.
     *
     * 답한 자리는 저쪽이 맞지만, **답하지 않은 자리까지 비우면 안 된다** — 우리가 보내서
     * 알고 있던 값이 모른다는 표시로 바뀐다. 원장에서 사실과 공백은 같은 모양이면 안 된다.
     */
    @Test
    fun `저쪽이 답하지 않은 자리에는 보낸 값이 남는다`() {
        val (_, task) = newTask(size = ImageSize.ByRatio(AspectRatio.SIXTEEN_NINE, Resolution.TWO_K))
        // 크기만 답하고 품질은 말하지 않는 Provider 다.
        val applied = AppliedParameters(width = 1024, height = 1024)

        workerWith(provider { success(applied = applied) }).process(task.id!!)

        val request = assertIs<ImageRequest>(callRepository.findAllByTaskUuid(task.uuid).single().request)
        assertEquals(1024, request.width, "답한 자리는 저쪽이 맞다")
        assertNotNull(request.quality, "답하지 않았다고 우리가 보낸 품질까지 지우면 안 된다")
    }

    /** 저쪽 청구서와 대조할 유일한 끈이다. 안 적으면 다시 알 길이 없다. */
    @Test
    fun `Provider 식별자가 결과물에 남는다`() {
        val (_, task) = newTask()

        workerWith(provider { success(providerId = "gen-abc") }).process(task.id!!)

        val file = generatedFileRepository.findAllByTaskId(task.id!!).single()
        assertEquals("gen-abc", assertNotNull(file.providerInfo).providerId)
    }

    @Test
    fun `참조한 이미지를 읽어 Provider 에 넘긴다`() {
        // 입력은 우리가 만든 것뿐이라, 먼저 글에서 하나 만든다.
        val (first, firstTask) = newTask()
        workerWith(provider { success() }).process(firstTask.id!!)
        val source = generatedFileRepository.findAllByTaskId(firstTask.id!!).single()

        val (_, editTask) = newTask(option = editOption(source.sha256), ownerUuid = first.ownerUserUuid)
        var captured: ExternalApiGenerateRequest? = null

        workerWith(
            object : ExternalApiProvider {
                override val name = PROVIDER
                override fun supports(model: String) = model == MODEL
                override fun generate(model: String, request: ExternalApiGenerateRequest): ExternalApiGenerateResponse {
                    captured = request
                    return success()
                }
            },
        ).process(editTask.id!!)

        val images = assertNotNull(captured).images
        assertEquals(1, images.size)
        assertContentEquals(image, assertIs<ExternalApiFileInput.Bytes>(images[0]).bytes, "보관소에서 읽은 바이트가 그대로 실려야 한다")
        assertEquals("image/png", images[0].mimeType)
        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(editTask.id!!).get().status)
    }

    @Test
    fun `참조한 이미지가 없으면 재시도하지 않는다`() {
        // 접수 때는 있었지만 그 사이 지워졌을 수 있다. 다시 읽어도 없으므로 그 자리에서 닫는다.
        val (_, task) = newTask(option = editOption(Sha256.of(UUID.randomUUID().toString().toByteArray()).hex))

        workerWith(provider { success() }).process(task.id!!)

        assertEquals(TaskStatus.FAILED, taskRepository.findById(task.id!!).get().status)
        assertTrue(storage.written.isEmpty(), "입력을 못 읽었는데 결과가 남았다")
    }

    /** 부르지 못했으니 나간 돈도 없다. 지출로 적히면 없는 비용이 집계에 섞인다. */
    @Test
    fun `입력을 읽지 못한 실패는 지출로 적히지 않는다`() {
        val (_, task) = newTask(option = editOption(Sha256.of(UUID.randomUUID().toString().toByteArray()).hex))

        workerWith(provider { success() }).process(task.id!!)

        assertTrue(callRepository.findAllByTaskUuid(task.uuid).isEmpty())
    }

    /** 바이트를 워커가 들고 있지 않다는 사실. 캐시를 넣으면 이 값이 1이 된다. */
    @Test
    fun `재시도하면 입력 이미지를 다시 읽는다`() {
        val (first, firstTask) = newTask()
        workerWith(provider { success() }).process(firstTask.id!!)
        val source = generatedFileRepository.findAllByTaskId(firstTask.id!!).single()

        val (_, editTask) = newTask(option = editOption(source.sha256), ownerUuid = first.ownerUserUuid)
        var attempts = 0
        val worker = workerWith(
            provider {
                attempts += 1
                if (attempts == 1) throw ExternalApiException(FailureKind.THROTTLED, "일시적 오류")
                success()
            },
        )

        val before = storage.reads
        worker.process(editTask.id!!)
        stateWriter.claim(limit = 1)
        worker.process(editTask.id!!)

        assertEquals(2, storage.reads - before)
    }

    @Test
    fun `입력 주소가 있으면 바이트를 읽지 않고 Provider에 전달하며 재시도마다 새로 발급한다`() {
        val (first, firstTask) = newTask()
        workerWith(provider { success() }).process(firstTask.id!!)
        val source = generatedFileRepository.findAllByTaskId(firstTask.id!!).single()
        val (_, task) = newTask(option = editOption(source.sha256), ownerUuid = first.ownerUserUuid)
        val urls = mutableListOf<URI>()
        var issued = 0
        val signer = PresignedUrlIssuer { key, filename ->
            assertEquals(source.storageKey, key)
            assertEquals("${source.sha256}.png", filename)
            URI.create("https://storage.test/$key?X-Amz-Signature=attempt-${++issued}")
        }
        val provider = object : ExternalApiProvider {
            override val name = PROVIDER
            override fun supports(model: String) = model == MODEL
            override fun generate(model: String, request: ExternalApiGenerateRequest): ExternalApiGenerateResponse {
                urls += assertIs<ExternalApiFileInput.Url>(request.images.single()).url
                if (urls.size == 1) throw ExternalApiException(FailureKind.THROTTLED, "일시적 오류")
                return success()
            }
        }
        val worker = workerWith(provider, presignedUrlIssuer = signer)
        val readsBefore = storage.reads
        worker.process(task.id!!)
        stateWriter.claim(limit = 1)
        worker.process(task.id!!)

        assertEquals(readsBefore, storage.reads, "주소를 보낼 때는 보관소 바이트를 앱이 읽지 않는다")
        assertEquals(2, issued)
        assertEquals(listOf("X-Amz-Signature=attempt-1", "X-Amz-Signature=attempt-2"), urls.map { it.query })
        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(task.id!!).get().status)
        assertFalse(jobRepository.findById(task.jobId).get().option.toString().contains("X-Amz-Signature"))
    }

    @Test
    fun `남의 입력에는 주소를 발급하거나 Provider를 호출하지 않는다`() {
        val (_, firstTask) = newTask()
        workerWith(provider { success() }).process(firstTask.id!!)
        val source = generatedFileRepository.findAllByTaskId(firstTask.id!!).single()
        val (_, task) = newTask(option = editOption(source.sha256))

        workerWith(
            provider { error("Provider를 호출하면 안 된다") },
            presignedUrlIssuer = PresignedUrlIssuer { _, _ -> error("주소를 발급하면 안 된다") },
        ).process(task.id!!)

        assertEquals(TaskStatus.FAILED, taskRepository.findById(task.id!!).get().status)
        assertTrue(callRepository.findAllByTaskUuid(task.uuid).isEmpty())
    }

    @Test
    fun `주소 발급 실패는 Provider 호출 없이 재시도 가능한 실패로 기록한다`() {
        val (first, firstTask) = newTask()
        workerWith(provider { success() }).process(firstTask.id!!)
        val source = generatedFileRepository.findAllByTaskId(firstTask.id!!).single()
        val (_, task) = newTask(option = editOption(source.sha256), ownerUuid = first.ownerUserUuid)

        workerWith(
            provider { error("Provider를 호출하면 안 된다") },
            presignedUrlIssuer = PresignedUrlIssuer { _, _ -> throw IllegalStateException("secret") },
        ).process(task.id!!)

        val failed = taskRepository.findById(task.id!!).get()
        assertEquals(TaskStatus.PENDING, failed.status)
        assertTrue(callRepository.findAllByTaskUuid(task.uuid).isEmpty())
    }

    @Test
    fun `올린 이미지는 upload 모듈이 발급한 주소로 넘기고 바이트를 읽지 않는다`() {
        val owner = UUID.randomUUID()
        val uploadUuid = Sha256.of(UUID.randomUUID().toString().toByteArray()).hex
        val uploads = object : UploadedFiles {
            override fun findUsable(sha256: String, ownerUuid: UUID) =
                UsableUpload("image/png").takeIf { sha256 == uploadUuid && ownerUuid == owner }

            override fun issueReadUrl(sha256: String, ownerUuid: UUID, fileName: String): URI {
                assertEquals("$uploadUuid.png", fileName)
                return URI.create("https://storage.test/upload?X-Amz-Signature=upload")
            }

            override fun readContent(sha256: String, ownerUuid: UUID) = error("주소로 넘길 수 있으면 바이트를 읽지 않는다")
        }
        var sent: URI? = null
        val provider = object : ExternalApiProvider {
            override val name = PROVIDER
            override fun supports(model: String) = model == MODEL
            override fun generate(model: String, request: ExternalApiGenerateRequest): ExternalApiGenerateResponse {
                sent = assertIs<ExternalApiFileInput.Url>(request.images.single()).url
                return success()
            }
        }
        val (_, task) = newTask(option = editOption(FileSource.Uploaded(uploadUuid)), ownerUuid = owner)
        val readsBefore = storage.reads

        workerWith(provider, uploadedFiles = uploads).process(task.id!!)

        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(task.id!!).get().status)
        assertEquals("X-Amz-Signature=upload", sent?.query)
        assertEquals(readsBefore, storage.reads)
    }

    @Test
    fun `쓸 수 없는 업로드는 주소를 받지 않고 Provider 도 부르지 않는다`() {
        val uploads = object : UploadedFiles {
            override fun findUsable(sha256: String, ownerUuid: UUID): UsableUpload? = null
            override fun issueReadUrl(sha256: String, ownerUuid: UUID, fileName: String) = error("주소를 받으면 안 된다")
            override fun readContent(sha256: String, ownerUuid: UUID) = error("바이트를 읽으면 안 된다")
        }
        val (_, task) = newTask(option = editOption(FileSource.Uploaded(Sha256.of(UUID.randomUUID().toString().toByteArray()).hex)))

        workerWith(provider { error("Provider를 호출하면 안 된다") }, uploadedFiles = uploads).process(task.id!!)

        assertEquals(TaskStatus.FAILED, taskRepository.findById(task.id!!).get().status)
        assertTrue(callRepository.findAllByTaskUuid(task.uuid).isEmpty())
    }

    /** 보관소가 Provider 에게 닿지 않는 환경을 흉내 낸다 — 주소로 받으면 받아가지 못했다고 답한다. */
    private fun unreachableByUrl(sent: MutableList<ExternalApiFileInput>) = object : ExternalApiProvider {
        override val name = PROVIDER
        override fun supports(model: String) = model == MODEL
        override fun generate(model: String, request: ExternalApiGenerateRequest): ExternalApiGenerateResponse {
            val input = request.images.single().also { sent += it }
            if (input is ExternalApiFileInput.Url) {
                throw ExternalApiException(FailureKind.INPUT_UNREACHABLE, "[fake 400] Error while downloading file")
            }
            return success()
        }
    }

    @Test
    fun `입력 주소를 받아가지 못하면 같은 입력을 바이트로 한 번 더 보낸다`() {
        val (first, firstTask) = newTask()
        workerWith(provider { success() }).process(firstTask.id!!)
        val source = generatedFileRepository.findAllByTaskId(firstTask.id!!).single()
        val (_, task) = newTask(option = editOption(source.sha256), ownerUuid = first.ownerUserUuid)
        val sent = mutableListOf<ExternalApiFileInput>()
        val signer = PresignedUrlIssuer { key, _ -> URI.create("https://storage.test/$key?X-Amz-Signature=s") }

        workerWith(unreachableByUrl(sent), presignedUrlIssuer = signer).process(task.id!!)

        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(task.id!!).get().status)
        assertIs<ExternalApiFileInput.Url>(sent[0])
        assertContentEquals(image, assertIs<ExternalApiFileInput.Bytes>(sent[1]).bytes)
        // 실패한 호출도 지출로 남는다. 지출 관점에서는 과금 없는 거절이다.
        val calls = callRepository.findAllByTaskUuid(task.uuid)
        assertEquals(1, calls.count { it.succeeded })
        assertEquals(RecordedFailureKind.REJECTED, calls.single { !it.succeeded }.failureKind)
    }

    @Test
    fun `올린 이미지도 주소로 실패하면 upload 모듈에서 바이트를 받아 다시 보낸다`() {
        val owner = UUID.randomUUID()
        val uploadUuid = Sha256.of(UUID.randomUUID().toString().toByteArray()).hex
        val uploads = object : UploadedFiles {
            override fun findUsable(sha256: String, ownerUuid: UUID) =
                UsableUpload("image/png").takeIf { sha256 == uploadUuid && ownerUuid == owner }

            override fun issueReadUrl(sha256: String, ownerUuid: UUID, fileName: String): URI =
                URI.create("https://storage.test/upload?X-Amz-Signature=upload")

            override fun readContent(sha256: String, ownerUuid: UUID) = image.takeIf { sha256 == uploadUuid && ownerUuid == owner }
        }
        val sent = mutableListOf<ExternalApiFileInput>()
        val (_, task) = newTask(option = editOption(FileSource.Uploaded(uploadUuid)), ownerUuid = owner)

        workerWith(unreachableByUrl(sent), uploadedFiles = uploads).process(task.id!!)

        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(task.id!!).get().status)
        assertEquals(2, sent.size)
        assertContentEquals(image, assertIs<ExternalApiFileInput.Bytes>(sent[1]).bytes)
    }

    @Test
    fun `처음부터 바이트로 보냈으면 다시 보내지 않는다`() {
        val (first, firstTask) = newTask()
        workerWith(provider { success() }).process(firstTask.id!!)
        val source = generatedFileRepository.findAllByTaskId(firstTask.id!!).single()
        val (_, task) = newTask(option = editOption(source.sha256), ownerUuid = first.ownerUserUuid)
        var calls = 0

        workerWith(
            provider {
                calls += 1
                throw ExternalApiException(FailureKind.INPUT_UNREACHABLE, "받아가지 못했다")
            },
        ).process(task.id!!)

        assertEquals(1, calls)
        assertEquals(TaskStatus.FAILED, taskRepository.findById(task.id!!).get().status)
    }

    private fun editOption(sourceSha256: String) = editOption(FileSource.Generated(sourceSha256))

    private fun editOption(source: FileSource) = ImageOption(
        prompt = "수채화로",
        sources = listOf(source),
        size = ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K),
    )

    private fun textProvider(behavior: (ExternalApiTextRequest) -> ExternalApiTextResponse) = object : ExternalApiTextProvider {
        override val name = PROVIDER
        override fun supports(model: String) = model == TEXT_MODEL
        override fun generateText(model: String, request: ExternalApiTextRequest) = behavior(request)
    }

    private fun textWorker(behavior: (ExternalApiTextRequest) -> ExternalApiTextResponse) =
        workerWith(provider { error("이미지 경로를 타면 안 된다") }, textProvider = textProvider(behavior))

    private val tokens = mapOf("input_tokens" to 12, "output_tokens" to 7)

    private fun answered(text: String, stopReason: StopReason = StopReason.COMPLETED) = ExternalApiTextResponse(
        text = text,
        stopReason = stopReason,
        usage = ProviderUsage(raw = tokens),
        providerId = "resp_1",
    )

    private val schema = """{"type":"object","properties":{"answer":{"type":"string"}}}"""

    @Test
    fun `글을 만들면 행에 남고 Job 이 닫힌다`() {
        val (job, task) = newTask(model = TEXT_MODEL, option = TextGenerateOption("세 줄로 요약해"))

        textWorker { answered("요약입니다") }.process(task.id!!)

        val text = generatedTextRepository.findAllByTaskIdIn(listOf(task.id!!)).single()
        assertEquals("요약입니다", text.content)
        assertEquals("resp_1", text.providerInfo?.providerId)
        assertTrue(storage.written.isEmpty(), "글은 보관소를 거치지 않는다")
        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(task.id!!).get().status)
        assertEquals(JobLifecycle.CLOSED, jobRepository.findById(job.id!!).get().status)
    }

    @Test
    fun `글 호출의 지출은 형식과 사용량으로 적힌다`() {
        val (_, task) = newTask(
            model = TEXT_MODEL,
            option = TextGenerateOption("풀어", output = TextFormat.JsonSchema(schema), effort = Effort.HIGH),
        )
        var sent: ExternalApiTextRequest? = null

        textWorker { sent = it; answered("""{"answer":"42"}""") }.process(task.id!!)

        // 스키마는 받은 원문 그대로 포트까지 간다.
        assertEquals(schema, assertNotNull(sent).jsonSchema)
        val call = callRepository.findAllByTaskUuid(task.uuid).single()
        assertTrue(call.succeeded)
        val recorded = assertIs<TextRequest>(call.request)
        assertEquals("json-schema", recorded.format)
        // 같은 모델에서도 비용을 가르는 축이라 원장에 남는다.
        assertEquals("high", recorded.effort)
        assertEquals(tokens, call.usageRaw)
    }

    /**
     * 거절도 저쪽은 만들었고 과금했다. Task 는 실패로 닫되, 지출은 사용량과 함께 성공 호출로 남아야 한다.
     * 다시 보내도 대개 같은 답이라 재시도하지 않는다.
     */
    @Test
    fun `거절된 글은 다시 하지 않고 닫되 지출은 사용량과 함께 남는다`() {
        val (job, task) = newTask(model = TEXT_MODEL, option = TextGenerateOption("질문"))

        textWorker { answered("도울 수 없다", StopReason.REFUSED) }.process(task.id!!)

        val failed = taskRepository.findById(task.id!!).get()
        assertEquals(TaskStatus.FAILED, failed.status)
        assertTrue(failed.failureReason!!.contains("REFUSED"), failed.failureReason)
        assertTrue(failed.failureReason!!.contains("도울 수 없다"), "거절 문구가 사유에서 사라졌다: ${failed.failureReason}")
        assertEquals(JobLifecycle.CLOSED, jobRepository.findById(job.id!!).get().status)
        assertTrue(generatedTextRepository.findAllByTaskIdIn(listOf(task.id!!)).isEmpty())

        val call = callRepository.findAllByTaskUuid(task.uuid).single()
        assertTrue(call.succeeded, "응답을 받은 호출을 실패로 적었다")
        assertEquals(tokens, call.usageRaw)
    }

    /** strict 로 보냈으니 일어나면 안 되는 일이다. 받는 쪽이 파싱하다 터지는 것보다 여기서 멈춘다. */
    @Test
    fun `스키마를 줬는데 JSON 이 아닌 답이면 결과로 받지 않는다`() {
        val (_, task) = newTask(model = TEXT_MODEL, option = TextGenerateOption("풀어", output = TextFormat.JsonSchema(schema)))

        textWorker { answered("42 입니다") }.process(task.id!!)

        val failed = taskRepository.findById(task.id!!).get()
        assertEquals(TaskStatus.FAILED, failed.status)
        assertTrue(generatedTextRepository.findAllByTaskIdIn(listOf(task.id!!)).isEmpty())
    }

    @Test
    fun `글 호출이 다시 해볼 만하게 실패하면 큐로 돌아간다`() {
        val (_, task) = newTask(model = TEXT_MODEL, option = TextGenerateOption("질문"))

        textWorker { throw ExternalApiException(FailureKind.THROTTLED, "429") }.process(task.id!!)

        assertEquals(TaskStatus.PENDING, taskRepository.findById(task.id!!).get().status)
        val call = callRepository.findAllByTaskUuid(task.uuid).single()
        assertFalse(call.succeeded)
        assertEquals(RecordedFailureKind.THROTTLED, call.failureKind)
        assertEquals("text", assertIs<TextRequest>(call.request).format)
    }

    @Test
    fun `글을 다룰 Provider 가 사라졌으면 닫는다`() {
        val (_, task) = newTask(model = TEXT_MODEL, option = TextGenerateOption("질문"))

        // 이미지 어댑터만 있다. 같은 이름의 모델이라도 글을 만들지 못한다.
        workerWith(provider { error("이미지 경로를 타면 안 된다") }).process(task.id!!)

        assertEquals(TaskStatus.FAILED, taskRepository.findById(task.id!!).get().status)
        assertTrue(callRepository.findAllByTaskUuid(task.uuid).isEmpty(), "부르지 않았는데 지출이 적혔다")
    }

    private companion object {
        const val MODEL = "fake-image-1"
        const val TEXT_MODEL = "fake-text-1"
        const val PROVIDER = "fake"

        /**
         * 재는지 안 재는지만 가리면 되므로 짧게 둔다. 다만 둘의 간격은 넉넉히 벌린다 —
         * 붙여 두면 CI 가 느리거나 GC 가 끼었을 때 간헐적으로 실패한다.
         */
        const val SLOW_MS = 200L
        const val THRESHOLD_MS = 100
    }
}
