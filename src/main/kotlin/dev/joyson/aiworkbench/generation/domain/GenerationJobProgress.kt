package dev.joyson.aiworkbench.generation.domain

/**
 * Job 이 어디까지 왔는지. 폴링하는 쪽이 "그만 물어봐도 되나" 를 판단하는 근거다.
 *
 * PENDING 과 RUNNING 을 구분하지 않는다 — 바깥에서 볼 때 둘 다 "아직" 이고,
 * 그 차이는 재시도 대기인지 처리 중인지라는 **우리 사정**이다.
 */
data class GenerationJobProgress(
    val total: Int,
    val succeeded: Int = 0,
    val failed: Int = 0,
) {
    /** 아직 끝나지 않은 개수. */
    val remaining: Int get() = total - succeeded - failed

    companion object {
        /**
         * Task 목록에서 센다.
         *
         * [total] 을 Task 개수가 아니라 Job 이 약속한 수로 받는 이유는, Task 가 아직
         * 다 만들어지지 않은 순간에도 "몇 장짜리 요청인가" 는 변하지 않기 때문이다.
         */
        fun of(total: Int, tasks: List<GenerationJobTask>): GenerationJobProgress =
            GenerationJobProgress(
                total = total,
                succeeded = tasks.count { it.status == TaskStatus.SUCCEEDED },
                failed = tasks.count { it.status == TaskStatus.FAILED },
            )
    }
}
