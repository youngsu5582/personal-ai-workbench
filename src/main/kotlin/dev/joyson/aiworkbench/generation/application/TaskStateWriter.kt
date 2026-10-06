package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.ProviderInfo
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.infrastructure.GeneratedFileRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** 저장까지 끝난 결과물 하나. */
data class StoredFile(
    val uuid: UUID,
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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 처리할 Task 를 집어 RUNNING 으로 바꾼다.
     */
    @Transactional
    fun claim(limit: Int): List<Long> {
        val claimed = taskRepository
            .findByStatusOrderByIdAsc(TaskStatus.PENDING, PageRequest.of(0, limit))
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
        val task = taskRepository.findById(taskId).orElse(null) ?: return

        generatedFileRepository.saveAll(
            files.map {
                GeneratedFile(
                    uuid = it.uuid,
                    taskId = taskId,
                    storageKey = it.storageKey,
                    metadata = it.metadata,
                    providerInfo = it.providerInfo,
                )
            },
        )
        task.transitionTo(TaskStatus.SUCCEEDED)
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
        val task = taskRepository.findById(taskId).orElse(null) ?: return

        task.transitionTo(TaskStatus.FAILED, failureReason = reason)

        val canRetry = retryable && task.attemptCount < GenerationJobTask.MAX_ATTEMPTS
        if (canRetry) {
            log.info("task 를 다시 큐에 넣는다. uuid={} 시도={} 사유={}", task.uuid, task.attemptCount, reason)
            task.transitionTo(TaskStatus.PENDING)
            return
        }

        log.warn("task 를 실패로 닫는다. uuid={} 시도={} 사유={}", task.uuid, task.attemptCount, reason)
        closeIfFinished(task)
    }

    private fun closeIfFinished(task: GenerationJobTask) {
        if (jobRepository.closeIfAllTasksFinished(task.jobId) > 0) {
            log.info("job 의 모든 task 가 끝났다. jobId={}", task.jobId)
        }
    }
}
