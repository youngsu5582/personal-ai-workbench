package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.infrastructure.GeneratedFileRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 저장까지 끝난 결과물 하나. */
data class StoredFile(
    val sequence: Int,
    val storageKey: String,
    val metadata: FileMetadata,
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
     *
     * **집는 쪽이 하나라는 것을 전제한다.** 지금은 스케줄러 스레드 하나가 직렬로 부르고,
     * 앞 배치가 끝나야 다음 폴이 시작한다. 그래서 조회와 상태 변경 사이에 끼어들 것이 없다.
     *
     * 그 전제가 깨지는 경우는 셋이다 — 인스턴스를 늘리거나, 폴링을 동시에 돌리거나,
     * 다른 곳에서 이 메서드를 부르거나. 그때는 조회-변경이 원자적이어야 하므로
     * `FOR UPDATE SKIP LOCKED` 나 조건부 UPDATE 로 바꿔야 한다.
     * (동시 처리 수를 늘리는 것은 해당되지 않는다 — 처리가 병렬일 뿐 집는 것은 그대로 하나다.)
     */
    @Transactional
    fun claim(limit: Int): List<Long> {
        val claimed = taskRepository
            .findByStatusOrderByIdAsc(TaskStatus.PENDING, PageRequest.of(0, limit))
            .onEach { it.transitionTo(TaskStatus.RUNNING) }
        // 같은 트랜잭션에서 다시 조회해도 PENDING 으로 보이지 않도록 여기서 확정한다.
        taskRepository.flush()
        return claimed.mapNotNull { it.id }
    }

    /**
     * 성공을 기록한다. Task 가 모두 끝났으면 Job 도 닫는다.
     *
     * 같은 Task 로 두 번 불릴 일은 없다 — SUCCEEDED 는 종료 상태라 재시도 대상이 아니고,
     * 실패한 시도는 여기까지 오지 않는다. 그래도 두 번 불린다면 그건 같은 Task 가 두 번
     * 처리됐다는 뜻이라, `UNIQUE(task_id, sequence)` 가 조용히 덮지 않고 터뜨리는 편이 낫다.
     */
    @Transactional
    fun succeed(taskId: Long, files: List<StoredFile>) {
        val task = taskRepository.findById(taskId).orElse(null) ?: return

        generatedFileRepository.saveAll(
            files.map {
                GeneratedFile(
                    taskId = taskId,
                    sequence = it.sequence,
                    storageKey = it.storageKey,
                    metadata = it.metadata,
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
        // 조건부 UPDATE 는 DB 의 현재 상태를 본다. 방금 바꾼 Task 상태가 아직 안 나갔으면
        // "아직 안 끝났다" 로 읽혀 Job 이 닫히지 않는다.
        taskRepository.flush()
        if (jobRepository.closeIfAllTasksFinished(task.jobId) > 0) {
            log.info("job 의 모든 task 가 끝났다. jobId={}", task.jobId)
        }
    }
}
