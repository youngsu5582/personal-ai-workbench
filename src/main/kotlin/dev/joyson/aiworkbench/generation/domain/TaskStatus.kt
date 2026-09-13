package dev.joyson.aiworkbench.generation.domain

/**
 * Task 하나의 처리 상태. Provider 와의 상호작용 1건이 어디까지 갔는가.
 *
 * [JobLifecycle] 과 값이 겹치지 않는다. 겹치면 코드에서 어느 쪽 상태인지
 * 문맥으로만 판단하게 되고, 한쪽에만 필요한 값이 다른 쪽에도 생긴다.
 */
enum class TaskStatus {
    /** 아직 Provider 에 제출되지 않았다. Worker 가 집어갈 대상이다. */
    PENDING,

    /** 제출됐고 결과를 기다린다. 폴링 대상이다. */
    RUNNING,

    SUCCEEDED,

    /** 실패했다. 재시도 정책에 따라 다시 PENDING 으로 돌아갈 수 있다. */
    FAILED,
    ;

    /** 이 Task 는 더 이상 Worker 의 관심 대상이 아니다. */
    val isFinished: Boolean get() = this == SUCCEEDED || this == FAILED

    fun canTransitionTo(next: TaskStatus): Boolean = next in allowedNext

    private val allowedNext: Set<TaskStatus>
        get() = when (this) {
            PENDING -> setOf(RUNNING, FAILED)
            RUNNING -> setOf(SUCCEEDED, FAILED)
            // 재시도는 실패한 Task 를 다시 큐에 넣는 것이다. 새 Task 를 만들지 않는다.
            FAILED -> setOf(PENDING)
            SUCCEEDED -> emptySet()
        }

    companion object {
        val FINISHED: Set<TaskStatus> = setOf(SUCCEEDED, FAILED)
    }
}
