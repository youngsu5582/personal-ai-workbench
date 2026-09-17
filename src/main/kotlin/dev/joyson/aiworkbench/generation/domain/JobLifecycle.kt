package dev.joyson.aiworkbench.generation.domain

/**
 * Job 의 수명. Task 결과의 요약이 **아니다**.
 *
 * 몇 장이 성공하고 몇 장이 실패했는지는 [GenerationJobProgress] 가 표현한다.
 * 여기에 SUCCEEDED·PARTIALLY_SUCCEEDED 를 두면 "몇 장 실패하면 부분 성공인가" 같은
 * 경계 규칙이 상태 전이에 섞여 들어온다.
 */
enum class JobLifecycle {
    /** 접수됨. 아직 Task 가 만들어지기 전. */
    PENDING,

    /** Task 가 만들어져 처리를 기다린다. */
    DISPATCHED,

    /** 모든 Task 가 종료 상태에 도달했다. 성공했다는 뜻이 아니라 더 움직이지 않는다는 뜻이다. */
    CLOSED,
}
