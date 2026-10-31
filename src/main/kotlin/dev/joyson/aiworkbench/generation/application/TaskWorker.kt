package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.ProviderInfo
import dev.joyson.aiworkbench.generation.domain.option.DecisionOption
import dev.joyson.aiworkbench.generation.domain.option.ImageOption
import dev.joyson.aiworkbench.generation.domain.option.TextFormat
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption
import dev.joyson.aiworkbench.storage.Sha256
import dev.joyson.aiworkbench.generation.domain.GenerationJobRepository
import dev.joyson.aiworkbench.generation.domain.GenerationJobTaskRepository
import dev.joyson.aiworkbench.storage.BlobKey
import dev.joyson.aiworkbench.provider.ExternalApiDecisionProvider
import dev.joyson.aiworkbench.provider.ExternalApiDecisionResponse
import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.ExternalApiTextResponse
import dev.joyson.aiworkbench.provider.FailureKind
import dev.joyson.aiworkbench.provider.ProviderRegistry
import dev.joyson.aiworkbench.provider.StopReason
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.FileStorageException
import dev.joyson.aiworkbench.usage.ProviderCallRecorder
import dev.joyson.aiworkbench.usage.RecordProviderCallCommand
import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.function.Supplier
import kotlin.time.TimeSource

/**
 * Task 하나를 끝까지 처리한다 — 번역 → Provider 호출 → 보관 → 기록.
 *
 * 종류마다 부르는 포트와 결과를 두는 곳이 달라 경로가 둘이다. 이미지는 보관소에 바이트를 쓰고,
 * 글은 행에 바로 둔다. 상태 전이 · 지출 기록 · 진행 중 지표는 두 경로가 같은 것을 쓴다.
 *
 * **트리거를 모른다.** 지금은 폴러가 부르고 나중에 메시지 컨슈머가 불러도 이 클래스는 그대로다.
 * 그래서 큐를 바꾸는 일이 "누가 `process` 를 부르는가" 한 줄로 끝난다.
 *
 * **트랜잭션을 걸지 않는다.** Provider 호출이 수십 초라 그 동안 커넥션을 붙잡으면 안 된다.
 * 상태를 적는 짧은 순간만 [TaskStateWriter] 가 연다.
 *
 * 지출 기록도 같은 이유로 [ProviderCallRecorder] 가 자기 트랜잭션을 연다.
 * 여기에 트랜잭션이 없어서 **기록 실패를 삼켜도 안전하다** — 주변 트랜잭션이 있었다면
 * 예외를 잡아도 이미 rollback-only 로 표시돼 커밋 시점에 다시 터졌을 것이다.
 */
@Component
class TaskWorker(
    private val taskRepository: GenerationJobTaskRepository,
    private val jobRepository: GenerationJobRepository,
    private val providerRegistry: ProviderRegistry,
    private val fileSourceResolver: FileSourceResolver,
    private val fileStorage: FileStorage,
    private val stateWriter: TaskStateWriter,
    private val callRecorder: ProviderCallRecorder,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun process(taskId: Long) {
        val task = taskRepository.findById(taskId) ?: return
        val job = jobRepository.findById(task.jobId) ?: run {
            // Task 는 있는데 Job 이 없는 건 설명되지 않는 상태다. 다시 해도 같으므로 닫는다.
            stateWriter.fail(taskId, "job 을 찾을 수 없다: ${task.jobId}", retryable = false)
            return
        }

        // 종류가 늘면 이 when 이 컴파일 에러로 알려준다.
        when (val option = job.option) {
            is ImageOption -> processImage(taskId, task, job, option)
            is TextGenerateOption -> processText(taskId, task, job, option)
            is DecisionOption -> processDecision(taskId, task, job, option)
        }
    }

    private fun processImage(taskId: Long, task: GenerationJobTask, job: GenerationJob, option: ImageOption) {
        // 접수 때 확인했더라도 그 사이 설정이 바뀌어 Provider 가 사라졌을 수 있다.
        val provider = providerRegistry.find(job.model) ?: run {
            stateWriter.fail(taskId, "다룰 수 있는 Provider 가 없다: ${job.model}", retryable = false)
            return
        }

        log.info("task 를 처리한다. uuid={} model={} 시도={}", task.uuid, job.model, task.attemptCount)

        val processMark = TimeSource.Monotonic.markNow()

        val response = callWithInputs(taskId, task, job) { images -> call(job, task, provider, option, images) } ?: return

        val stored = try {
            store(job, response)
        } catch (e: FileStorageException) {
            stateWriter.fail(taskId, e.message ?: "결과를 보관하지 못했다", e.retryable)
            return
        }

        stateWriter.succeed(taskId, stored)
        log.info(
            "task 처리를 완료했다. uuid={} 소요={}ms 파일={}",
            task.uuid,
            processMark.elapsedMillis(),
            stored.map { it.storageKey },
        )
    }

    /**
     * 글 한 편을 만든다. 입력 파일도 보관소도 없어 이미지 경로보다 짧다.
     *
     * 응답이 왔으면 지출은 **성공 호출로** 적는다 — 거절이나 잘림도 저쪽은 만들었고 과금했다.
     * 그 글을 결과로 받을지는 그 다음에 정한다.
     */
    private fun processText(taskId: Long, task: GenerationJobTask, job: GenerationJob, option: TextGenerateOption) {
        // 접수 때 확인했더라도 그 사이 설정이 바뀌어 Provider 가 사라졌을 수 있다.
        val provider = providerRegistry.findText(job.model) ?: run {
            stateWriter.fail(taskId, "다룰 수 있는 Provider 가 없다: ${job.model}", retryable = false)
            return
        }

        log.info("task 를 처리한다. uuid={} model={} 시도={}", task.uuid, job.model, task.attemptCount)

        val processMark = TimeSource.Monotonic.markNow()
        val request = ProviderRequestMapper.from(option)

        // 이미지 경로의 call 과 같은 순서다 — 벽시계로 시각을, 단조 시계로 길이를 잰다.
        val calledAt = Instant.now()
        val callMark = TimeSource.Monotonic.markNow()
        val response = try {
            activeCalls(provider.name, job.model).record(Supplier { provider.generateText(job.model, request) })
        } catch (e: ExternalApiException) {
            record(
                ProviderCallMapper.failed(
                    job, task, provider.name, request,
                    callMark.elapsedMillis(), calledAt, e.message ?: CALL_FAILED, e.kind,
                ),
            )
            failCall(taskId, e)
            return
        }
        record(ProviderCallMapper.succeeded(job, task, provider.name, request, callMark.elapsedMillis(), calledAt, response.usage))

        unusableReason(response, option.output)?.let { reason ->
            // 같은 요청을 다시 보내도 대개 같은 답이 온다. 다시 하면 돈만 또 나간다.
            stateWriter.fail(taskId, reason, retryable = false)
            return
        }

        stateWriter.succeed(
            taskId,
            response.text,
            ProviderInfo(providerId = response.providerId).takeIf { !it.isEmpty },
        )
        log.info("task 처리를 완료했다. uuid={} 소요={}ms 글={}자", task.uuid, processMark.elapsedMillis(), response.text.length)
    }

    /**
     * 근거를 정해 둔 질문으로 판정한다. 근거 이미지가 있으면 이미지 경로처럼 준비해 넘긴다.
     *
     * 어떤 질문에 답을 거절한 것은 Task 의 실패가 아니다 — 거절도 답의 하나로 남고, 나머지 답은 쓸 수 있다.
     */
    private fun processDecision(taskId: Long, task: GenerationJobTask, job: GenerationJob, option: DecisionOption) {
        // 접수 때 확인했더라도 그 사이 설정이 바뀌어 Provider 가 사라졌을 수 있다.
        val provider = providerRegistry.findDecision(job.model) ?: run {
            stateWriter.fail(taskId, "다룰 수 있는 Provider 가 없다: ${job.model}", retryable = false)
            return
        }

        log.info("task 를 처리한다. uuid={} model={} 시도={}", task.uuid, job.model, task.attemptCount)

        val processMark = TimeSource.Monotonic.markNow()
        val response = callWithInputs(taskId, task, job) { images -> callDecision(job, task, provider, option, images) } ?: return

        stateWriter.succeed(
            taskId,
            DecisionAnswerMapper.from(response.answers),
            ProviderInfo(providerId = response.providerId).takeIf { !it.isEmpty },
        )
        log.info("task 처리를 완료했다. uuid={} 소요={}ms 답={}개", task.uuid, processMark.elapsedMillis(), response.answers.size)
    }

    /** 이미지 경로의 [call] 과 같은 순서다 — 실패한 호출도 적고, 성공은 결과를 쓰기 전에 적는다. */
    private fun callDecision(
        job: GenerationJob,
        task: GenerationJobTask,
        provider: ExternalApiDecisionProvider,
        option: DecisionOption,
        images: List<ResolvedFile>,
    ): ExternalApiDecisionResponse {
        val request = ProviderRequestMapper.from(option, images)

        val calledAt = Instant.now()
        val callMark = TimeSource.Monotonic.markNow()
        val response = try {
            activeCalls(provider.name, job.model).record(Supplier { provider.decide(job.model, request) })
        } catch (e: ExternalApiException) {
            record(
                ProviderCallMapper.failed(
                    job, task, provider.name, request,
                    callMark.elapsedMillis(), calledAt, e.message ?: CALL_FAILED, e.kind,
                ),
            )
            throw e
        }

        record(ProviderCallMapper.succeeded(job, task, provider.name, request, callMark.elapsedMillis(), calledAt, response.usage))
        return response
    }

    /**
     * 입력 파일을 준비해 [send] 를 부른다. 실패하면 Task 를 닫고 null 이다.
     *
     * 호출 전에 소유권을 다시 확인하고 바이트 또는 새 읽기 주소를 준비한다.
     * 주소를 큐에 저장하면 대기 중 만료되므로 실행과 재시도마다 발급한다.
     *
     * 주소로 넘긴 입력을 저쪽이 받아가지 못했으면 같은 입력을 바이트로 실어 한 번만 더 보낸다.
     * 처음부터 바이트였다면 다시 보내도 같다.
     */
    private fun <T> callWithInputs(
        taskId: Long,
        task: GenerationJobTask,
        job: GenerationJob,
        send: (List<ResolvedFile>) -> T,
    ): T? {
        val images = prepareInputs(taskId, job, inline = false) ?: return null
        return try {
            send(images)
        } catch (e: ExternalApiException) {
            if (e.kind != FailureKind.INPUT_UNREACHABLE || images.none { it is ResolvedFile.Url }) {
                failCall(taskId, e)
                return null
            }
            log.warn("Provider 가 입력 주소를 받아가지 못해 바이트로 다시 보낸다. uuid={}", task.uuid)
            val inlined = prepareInputs(taskId, job, inline = true) ?: return null
            try {
                send(inlined)
            } catch (retried: ExternalApiException) {
                failCall(taskId, retried)
                null
            }
        }
    }

    /**
     * 받은 글을 결과로 쓸 수 없는 이유. 쓸 수 있으면 null 이다.
     *
     * 잘린 글은 형식이 없으면 읽을 수는 있지만 받지 않는다 — 받는 쪽이 그것이 잘린 줄 알 길이 없다.
     * 사유에 글을 조금 붙인다. 거절이면 그 문구가 왜 실패했는지를 말해 준다.
     */
    private fun unusableReason(response: ExternalApiTextResponse, format: TextFormat): String? = when {
        response.stopReason != StopReason.COMPLETED ->
            "모델이 끝까지 답하지 않았다(${response.stopReason}): ${response.text.take(REASON_EXCERPT)}"
        // strict 로 보냈으니 일어나면 안 되는 일이다. 받는 쪽이 파싱하다 터지는 것보다 여기서 멈춘다.
        !format.conforms(response.text) -> "요청한 형식이 아닌 답이 왔다: ${response.text.take(REASON_EXCERPT)}"
        else -> null
    }

    /** 준비에 실패하면 이번 호출은 나가지 않는다 — 나간 돈이 없으므로 지출도 적지 않는다. */
    private fun prepareInputs(taskId: Long, job: GenerationJob, inline: Boolean): List<ResolvedFile>? =
        try {
            fileSourceResolver.resolve(job.ownerUserUuid, job.option, inline)
        } catch (e: FileSourceUnavailableException) {
            stateWriter.fail(taskId, e.message ?: "입력 이미지를 읽지 못했다", e.retryable)
            null
        } catch (e: FileStorageException) {
            stateWriter.fail(taskId, e.message ?: "입력 이미지를 읽지 못했다", e.retryable)
            null
        }

    /**
     * Provider 를 한 번 부르고 그 호출을 적는다.
     *
     * 실패한 호출도 적는다. 타임아웃은 우리만 못 받았을 뿐 저쪽에서는 만들어졌고 과금됐을 수 있다.
     * 여기서 안 적으면 가장 설명이 필요한 지출이 기록에서 사라진다.
     *
     * 성공은 보관보다 **먼저** 적는다. 돈은 이미 나갔고 그 사실은 뒤에 무엇이 실패하든 남아야 한다.
     * 파일을 상태보다 먼저 쓰는 것과 같은 순서 — 되돌릴 수 없는 일이 먼저 기록된다.
     */
    private fun call(
        job: GenerationJob,
        task: GenerationJobTask,
        provider: ExternalApiProvider,
        option: ImageOption,
        images: List<ResolvedFile>,
    ): ExternalApiGenerateResponse {
        val request = ProviderRequestMapper.from(option, images)

        // 호출만 따로 잰다. 보관 지연이 섞이면 이 숫자로 Provider 를 비교할 수 없다.
        // 기록에 남는 시각은 벽시계지만 길이는 단조 시계로 잰다 — 시계가 보정되면 음수 지연이 나온다.
        val calledAt = Instant.now()
        val callMark = TimeSource.Monotonic.markNow()
        val response = try {
            // 도는 동안만 진행 중에 둔다. record 가 성공이든 예외든 끝나면 뺀다.
            activeCalls(provider.name, job.model).record(Supplier { provider.generate(job.model, request) })
        } catch (e: ExternalApiException) {
            record(
                ProviderCallMapper.failed(
                    job, task, provider.name, request,
                    callMark.elapsedMillis(), calledAt, e.message ?: CALL_FAILED, e.kind,
                ),
            )
            throw e
        }

        record(ProviderCallMapper.succeeded(job, task, provider.name, request, callMark.elapsedMillis(), calledAt, response.usage, response.applied))
        return response
    }

    /**
     * 진행 중인 Provider 호출. 끝난 호출만 재는 Timer 로는 멈춘 호출이 타임아웃이 나야 보이는데,
     * 여기서는 몇 초째 돌고 있는지가 도는 동안 보인다.
     *
     * 끝나면 반드시 빠져야 한다 — 남으면 끝나지 않은 호출처럼 나이가 계속 는다. 그래서 start·stop 을
     * 손으로 짝짓지 않고 `record` 로 감싼다.
     */
    private fun activeCalls(providerName: String, model: String): LongTaskTimer =
        LongTaskTimer.builder("workbench.provider.call.active")
            .description(ACTIVE_CALL_DESCRIPTION)
            .tags("provider", providerName, "model", model)
            .register(meterRegistry)

    private fun failCall(taskId: Long, e: ExternalApiException) =
        stateWriter.fail(taskId, e.message ?: CALL_FAILED, e.retryable)

    /**
     * 지출을 적는다. **실패해도 Task 를 죽이지 않는다.**
     *
     * 돈은 이미 나간 뒤다. 여기서 예외를 올리면 Task 가 실패 처리되고 재시도가 돈을 **또** 쓴다 —
     * 원장 한 줄을 잃는 것보다 나쁘다. 그래서 잃은 사실을 로그로 크게 남기고 진행한다.
     *
     * 성공도 INFO 로 남긴다. 이 행이 돈이 나갔다는 **유일한 흔적**이라, 로그에 아무 말이 없으면
     * 적히지 않은 것인지 적히고 나서 사라진 것인지 구분할 방법이 없다.
     * Provider 호출은 건당 수십 초라 호출마다 한 줄이 늘어도 소음이 되지 않는다.
     *
     * 사용량 유무를 함께 찍는 이유: 토큰이 없으면 나중에 단가를 곱할 것이 없어
     * 그 행은 금액을 영영 못 채운다. 그 사실이 지금 드러나야 한다.
     *
     * 성공 쪽에 원자료를 찍지 않는 이유: 이 로그가 찍혔다는 건 행이 이미 있다는 뜻이고,
     * 원자료는 `usage_raw` 에 온전히 들어 있다. 크기를 Provider 가 정하는 값을 굳이 두 번 쓰지 않는다.
     * 반대로 **실패 쪽에는 커맨드를 통째로** 남긴다 — 그때는 이 줄이 마지막 사본이다.
     * 응답은 한 번뿐이라 여기서 안 남기면 그 호출의 토큰 수는 영영 사라진다.
     */
    private fun record(command: RecordProviderCallCommand) {
        // runCatching 을 쓰지 않는다 — 그건 Throwable 을 잡아서 OutOfMemoryError 까지 삼킨다.
        // 여기서 삼켜도 되는 것은 "적지 못했다" 지 "JVM 이 무너졌다" 가 아니다.
        try {
            val recorded = callRecorder.record(command)
            log.info(
                "지출을 적었다. call={} model={} 성공={} 소요={}ms 사용량={}",
                recorded, command.model, command.succeeded, command.latencyMs,
                if (command.usageRaw.isNullOrEmpty()) "없음" else "있음",
            )
        } catch (e: Exception) {
            log.error("지출을 기록하지 못했다. 이 줄이 마지막 사본이다. {}", command, e)
        }
    }

    /** `latency_ms` 가 integer 라 여기서 좁힌다. 읽기 타임아웃이 분 단위라 넘칠 길이 없다. */
    private fun TimeSource.Monotonic.ValueTimeMark.elapsedMillis(): Int =
        elapsedNow().inWholeMilliseconds.toInt()

    /**
     * 바이트를 보관소에 쓰고 기록할 정보만 남긴다.
     *
     * 상태 기록보다 **먼저** 한다. 순서를 뒤집으면 SUCCEEDED 인데 파일이 없는 행이 생길 수 있고,
     * 그건 목록에서 깨진 이미지로만 드러난다. 반대 순서의 실패(파일은 있는데 기록이 없음)는
     * 아무도 가리키지 않는 파일 하나로 끝난다 — 눈에 띄지 않고, 나중에 정리 작업이 걷어낸다.
     */
    private fun store(
        job: GenerationJob,
        response: ExternalApiGenerateResponse,
    ): List<StoredFile> = response.result.map { result ->
        // 자리는 내용이 정한다 — 같은 바이트면 같은 키라 이 보관이 멱등하다. 다시 돌려도 고아가 안 생긴다.
        val digest = Sha256.of(result.image)
        val key = BlobKey.of(
            ownerUuid = job.ownerUserUuid,
            digest = digest,
            mimeType = result.metadata.mimeType,
        ).value
        fileStorage.put(key, result.image, result.metadata.mimeType)

        StoredFile(
            sha256 = digest.hex,
            storageKey = key,
            metadata = FileMetadata(
                width = result.metadata.width,
                height = result.metadata.height,
                mimeType = result.metadata.mimeType,
                fileSize = result.metadata.fileSize,
            ),
            // 포트가 이미 갈라 둔 것을 저장하면서 합치지 않는다. 바이트에서 나오는 것과
            // 저쪽이 말해줘야만 아는 것은 같은 칸에 있으면 안 된다.
            providerInfo = ProviderInfo(
                revisedPrompt = result.revisedPrompt,
                providerId = result.providerId,
            ).takeIf { !it.isEmpty },
        )
    }

    private companion object {
        const val CALL_FAILED = "Provider 호출이 실패했다"

        /** 실패 사유에 붙일 글의 길이. 사유 칸이 255자라 앞의 설명과 함께 들어가야 한다. */
        const val REASON_EXCERPT = 100

        /** `_count` 가 같은 이름의 Timer 와 뜻이 다르다. 누적 호출 수로 읽으면 안 된다. */
        const val ACTIVE_CALL_DESCRIPTION =
            "지금 진행 중인 Provider 호출. 끝나면 빠진다 — _count 는 누적이 아니라 지금 수, _max 는 가장 오래 돌고 있는 호출의 초다"
    }
}
