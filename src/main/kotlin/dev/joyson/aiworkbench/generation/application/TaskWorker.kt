package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.ProviderInfo
import dev.joyson.aiworkbench.generation.domain.Sha256
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import dev.joyson.aiworkbench.generation.infrastructure.ProviderCallFactory
import dev.joyson.aiworkbench.generation.infrastructure.ProviderRequestFactory
import dev.joyson.aiworkbench.generation.infrastructure.StorageKeys
import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ProviderRegistry
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.FileStorageException
import dev.joyson.aiworkbench.usage.ProviderCallRecorder
import dev.joyson.aiworkbench.usage.RecordProviderCallCommand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import kotlin.time.TimeSource

/**
 * Task 하나를 끝까지 처리한다 — 번역 → Provider 호출 → 보관 → 기록.
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
    private val fileStorage: FileStorage,
    private val stateWriter: TaskStateWriter,
    private val callRecorder: ProviderCallRecorder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun process(taskId: Long) {
        val task = taskRepository.findById(taskId).orElse(null) ?: return
        val job = jobRepository.findById(task.jobId).orElse(null) ?: run {
            // Task 는 있는데 Job 이 없는 건 설명되지 않는 상태다. 다시 해도 같으므로 닫는다.
            stateWriter.fail(taskId, "job 을 찾을 수 없다: ${task.jobId}", retryable = false)
            return
        }

        // 접수 때 확인했더라도 그 사이 설정이 바뀌어 Provider 가 사라졌을 수 있다.
        val provider = providerRegistry.find(job.model) ?: run {
            stateWriter.fail(taskId, "다룰 수 있는 Provider 가 없다: ${job.model}", retryable = false)
            return
        }

        log.info("task 를 처리한다. uuid={} model={} 시도={}", task.uuid, job.model, task.attemptCount)

        val request = ProviderRequestFactory.from(job.option)
        val processMark = TimeSource.Monotonic.markNow()

        // 호출만 따로 잰다. 보관 지연이 섞이면 이 숫자로 Provider 를 비교할 수 없다.
        // 기록에 남는 시각은 벽시계지만 길이는 단조 시계로 잰다 — 시계가 보정되면 음수 지연이 나온다.
        val calledAt = Instant.now()
        val callMark = TimeSource.Monotonic.markNow()
        val response = try {
            provider.generate(job.model, request)
        } catch (e: ExternalApiException) {
            val reason = e.message ?: "Provider 호출이 실패했다"
            // 실패한 호출도 적는다. 타임아웃은 우리만 못 받았을 뿐 저쪽에서는 만들어졌고 과금됐을 수 있다.
            // 여기서 안 적으면 가장 설명이 필요한 지출이 기록에서 사라진다.
            record(
                ProviderCallFactory.failed(
                    job, task, provider.name, request,
                    callMark.elapsedMillis(), calledAt, reason, e.kind,
                ),
            )
            stateWriter.fail(taskId, reason, e.retryable)
            return
        }

        // 보관보다 **먼저** 적는다. 돈은 이미 나갔고 그 사실은 뒤에 무엇이 실패하든 남아야 한다.
        // 파일을 상태보다 먼저 쓰는 것과 같은 순서 — 되돌릴 수 없는 일이 먼저 기록된다.
        record(ProviderCallFactory.succeeded(job, task, provider.name, request, callMark.elapsedMillis(), calledAt, response.usage, response.applied))

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
        val key = StorageKeys.generatedFile(
            ownerUuid = job.ownerUserUuid,
            digest = Sha256.of(result.image),
            mimeType = result.metadata.mimeType,
        )
        fileStorage.put(key, result.image, result.metadata.mimeType)

        // 행의 식별자는 따로 둔다. digest 에서 파생시키면 같은 바이트를 가진 두 행이
        // uk_generated_files_uuid 에서 충돌한다 — 키는 내용 주소, 행 식별자는 랜덤이다.
        val fileUuid = UUID.randomUUID()

        StoredFile(
            uuid = fileUuid,
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
}
