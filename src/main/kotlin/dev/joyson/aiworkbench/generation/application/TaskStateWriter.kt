package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.ProviderInfo
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.domain.GeneratedFileRepository
import dev.joyson.aiworkbench.generation.domain.GeneratedText
import dev.joyson.aiworkbench.generation.domain.GeneratedTextRepository
import dev.joyson.aiworkbench.generation.domain.GenerationJobRepository
import dev.joyson.aiworkbench.generation.domain.GenerationJobTaskRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID

/** 저장까지 끝난 결과물 하나. */
data class StoredFile(
    val sha256: String,
    val storageKey: String,
    val metadata: FileMetadata,
    /** 저쪽이 아무것도 알려주지 않으면 null 이다. */
    val providerInfo: ProviderInfo? = null,
)

/**
 * Task 의 상태 전이를 **짧은 트랜잭션**으로 처리한다.
 *
 * 워커와 나눠 둔 이유는 트랜잭션 길이 때문이다. Provider 호출은 수십 초가 걸리는데
 * 그 동안 트랜잭션이 열려 있으면 커넥션과 행 잠금을 그만큼 붙잡는다.
 * 그래서 집어들 때 한 번, 결과를 적을 때 한 번만 연다.
 *
 * (스프링 프록시는 같은 객체 안의 호출에 트랜잭션을 걸지 않는다. 별도 빈이어야 하는 이유이기도 하다.)
 */
@Component
class TaskStateWriter(
    private val taskRepository: GenerationJobTaskRepository,
    private val jobRepository: GenerationJobRepository,
    private val generatedFileRepository: GeneratedFileRepository,
    meterRegistry: MeterRegistry,
    private val generatedTextRepository: GeneratedTextRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 시도 한 번의 길이를 결과별로 잰다. 재시도율도 여기서 나온다 — `outcome="retried"` 의 개수다.
     *
     * 결과 셋을 기동할 때 만들어 둔다. 처음 생길 때까지 시계열이 없으면 "0 번" 과 "모른다" 가 구분되지 않는다.
     */
    private val attempts: Map<String, Timer> = listOf(SUCCEEDED, RETRIED, FAILED).associateWith { outcome ->
        Timer.builder("workbench.generation.task.attempt")
            .tag("outcome", outcome)
            .serviceLevelObjectives(*ATTEMPT_BUCKETS)
            .register(meterRegistry)
    }

    /**
     * 처리할 Task 를 집어 RUNNING 으로 바꾼다.
     */
    @Transactional
    fun claim(limit: Int): List<Long> {
        val claimed = taskRepository
            .findOldestByStatus(TaskStatus.PENDING, limit)
            .onEach { it.transitionTo(TaskStatus.RUNNING) }
        if (claimed.isEmpty()) return emptyList()

        log.debug("task 를 집어 RUNNING 으로 옮겼다. uuidList={}", claimed.mapNotNull { it.uuid })
        return claimed.mapNotNull { it.id }
    }

    /**
     * 성공을 기록한다. Task 가 모두 끝났으면 Job 도 닫는다.
     *
     * 같은 Task 로 두 번 불릴 일은 없다 — 스케줄러가 한 곳에서 돌고, `claim` 은 PENDING 만 집으며,
     * 재시도는 FAILED 를 거치므로 앞 시도는 이미 끝나 있다.
     *
     * 이 전제가 깨지는 첫 시점은 **RUNNING 고착을 회수하는 기능을 만들 때**다. 회수가 아직 살아 있는
     * Task 를 되돌리면 두 워커가 겹친다. 그때 이 전이를 조건부 UPDATE(`... AND status = 'RUNNING'`)로
     * 바꿔 이긴 쪽만 파일을 쓰게 한다 — Job 을 닫을 때 쓰는 것과 같은 방법이다.
     */
    @Transactional
    fun succeed(taskId: Long, files: List<StoredFile>) {
        val task = taskRepository.findById(taskId) ?: return

        generatedFileRepository.saveAll(
            files.map {
                GeneratedFile(
                    sha256 = it.sha256,
                    taskId = taskId,
                    storageKey = it.storageKey,
                    metadata = it.metadata,
                    providerInfo = it.providerInfo,
                )
            },
        )
        task.transitionTo(TaskStatus.SUCCEEDED)
        recordAttempt(task, SUCCEEDED)
        closeIfFinished(task)
    }

    /**
     * 글 한 편으로 성공을 기록한다. 규칙은 파일로 끝난 [succeed] 와 같다.
     *
     * 보관소를 거치지 않으니 결과 행과 상태 전이가 한 트랜잭션에 든다 — 성공인데 글이 없는 행이 생길 틈이 없다.
     */
    @Transactional
    fun succeed(taskId: Long, text: String, providerInfo: ProviderInfo?) {
        val task = taskRepository.findById(taskId) ?: return

        generatedTextRepository.save(GeneratedText(taskId = taskId, content = text, providerInfo = providerInfo))
        task.transitionTo(TaskStatus.SUCCEEDED)
        recordAttempt(task, SUCCEEDED)
        closeIfFinished(task)
    }

    /**
     * 실패를 기록한다. 다시 해볼 만하고 시도 여유가 남았으면 큐로 되돌린다.
     *
     * RUNNING 에서 곧바로 PENDING 으로 갈 수 없어 FAILED 를 거친다 —
     * 상태 전이 규칙이 그렇게 정해져 있다(`TaskStatus`).
     */
    @Transactional
    fun fail(taskId: Long, reason: String, retryable: Boolean) {
        val task = taskRepository.findById(taskId) ?: return

        task.transitionTo(TaskStatus.FAILED, failureReason = reason)

        val canRetry = retryable && task.attemptCount < GenerationJobTask.MAX_ATTEMPTS
        // 되돌릴지는 여기서만 정해지므로 결과도 여기서 붙인다. 큐로 되돌리면 끝난 시각이 지워지니 그 전에 잰다.
        recordAttempt(task, if (canRetry) RETRIED else FAILED)

        if (canRetry) {
            log.info("task 를 다시 큐에 넣는다. uuid={} 시도={} 사유={}", task.uuid, task.attemptCount, reason)
            task.transitionTo(TaskStatus.PENDING)
            return
        }

        log.warn("task 를 실패로 닫는다. uuid={} 시도={} 사유={}", task.uuid, task.attemptCount, reason)
        closeIfFinished(task)
    }

    /**
     * 집히기 전에 실패한 Task 는 시작 시각이 없다. 재지 않은 것은 세지 않는다.
     *
     * 길이는 저장한 벽시계 두 시각의 차라, 그 사이 시계가 뒤로 보정되면 음수가 된다.
     * Timer 는 음수를 버리고 그러면 결과별 건수(재시도율)까지 빠진다 — 0 으로 올려 건수는 지킨다.
     */
    private fun recordAttempt(task: GenerationJobTask, outcome: String) {
        val started = task.startedAt ?: return
        val finished = task.finishedAt ?: return
        attempts.getValue(outcome).record(Duration.between(started, finished).coerceAtLeast(Duration.ZERO))
    }

    private fun closeIfFinished(task: GenerationJobTask) {
        if (jobRepository.closeIfAllTasksFinished(task.jobId) > 0) {
            log.info("job 의 모든 task 가 끝났다. jobId={}", task.jobId)
        }
    }

    private companion object {
        const val SUCCEEDED = "succeeded"
        const val RETRIED = "retried"
        const val FAILED = "failed"

        /** 시도 한 번은 Provider 호출과 보관을 합친 길이라 건당 수십 초다. 그 범위에 맞춘 여섯 개만 둔다. */
        val ATTEMPT_BUCKETS: Array<Duration> =
            arrayOf(5L, 10L, 30L, 60L, 120L, 300L).map(Duration::ofSeconds).toTypedArray()
    }
}
