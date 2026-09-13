package dev.joyson.aiworkbench.generation.domain

/**
 * Job 의 **수명**. Task 결과의 요약이 아니다.
 *
 * "성공했나 / 몇 개나 됐나" 는 여기서 답하지 않는다 — Task 를 집계해 응답에서 파생한다.
 * 두 상태가 같은 것을 두 번 말하면 반드시 어긋나기 때문이다.
 *
 * 이 컬럼이 존재하는 이유는 둘뿐이다.
 * - "아직 처리할 게 남은 Job" 을 인덱스로 찾기 위해
 * - 완료 순간에 훅(SSE 푸시 등)을 걸 지점을 갖기 위해
 */
enum class JobLifecycle {
    /** 접수됐고 아직 Task 로 갈라지지 않았다. */
    PENDING,

    /** Task 가 만들어져 처리 중이다. */
    DISPATCHED
}
