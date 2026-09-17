package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.application.TaskStateWriter
import dev.joyson.aiworkbench.generation.application.TaskWorker
import dev.joyson.aiworkbench.generation.config.GenerationWorkerProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 큐 테이블에서 할 일을 꺼내 [TaskWorker] 에 넘긴다.
 *
 * **Task 테이블이 곧 outbox 다.** 접수 트랜잭션이 Job 과 Task 를 함께 쓰고, 여기가 그것을 읽어
 * 내보낸다. 지금 내보내는 곳이 인프로세스 워커일 뿐, 브로커가 생기면 이 자리에서 메시지를 보낸다 —
 * 바뀌는 것은 **넘기는 곳**이지 꺼내는 방식이 아니다.
 *
 * 별도 outbox 테이블을 두지 않는 이유: Task 행이 이미 "해야 할 일" 이고 상태도 거기 있다.
 * 하나 더 두면 같은 사실이 두 곳에 남고, 둘을 맞추는 일이 새로 생긴다.
 *
 * 이벤트 발행만 하고 끝내지 않는 이유: 인프로세스 이벤트는 재시작·장애에 유실된다.
 * 그러면 PENDING 인 Task 를 아무도 집지 않는 채로 남는다. 테이블이 진실이라 꺼내는 쪽은
 * 항상 테이블을 본다.
 *
 * 얇게 유지한다. 일은 워커가 하고 여기는 꺼내서 넘기는 것만 한다.
 */
@Component
@ConditionalOnProperty(
    prefix = "workbench.generation.worker",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class TaskPoller(
    private val stateWriter: TaskStateWriter,
    private val worker: TaskWorker,
    private val properties: GenerationWorkerProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${workbench.generation.worker.poll-interval-millis:3000}",
        initialDelayString = "\${workbench.generation.worker.initial-delay-millis:5000}",
    )
    fun poll() {
        val claimed = stateWriter.claim(properties.batchSize)
        if (claimed.isEmpty()) return

        log.debug("task {}건을 집었다", claimed.size)
        claimed.forEach { taskId ->
            // 한 건이 터져도 나머지는 계속 간다. 여기서 못 잡으면 스케줄러가 멈춘다.
            runCatching { worker.process(taskId) }
                .onFailure { log.error("task 처리 중 예기치 못한 오류. taskId={}", taskId, it) }
        }
    }
}
