package dev.joyson.aiworkbench.provider

import java.math.BigDecimal

/**
 * Provider 가 이 호출에 대해 말한 것.
 *
 * **사용량과 비용을 한 필드에 담지 않는다.** 토큰 수는 사용량이지 비용이 아니다.
 * 한 칸에 합치면 토큰을 쓰는 Provider 와 크레딧을 쓰는 Provider 의 값이 같은 자리에 섞여
 * 합계가 뜻 없는 숫자가 된다.
 *
 * 무엇을 주는지는 Provider 마다 다르다. 셋 다 가능하다 —
 * 사용량만(OpenAI), 비용만, 아무것도 안 줌.
 */
data class ProviderUsage(
    /**
     * 응답의 사용량 블록 **원문 그대로**.
     *
     * 우리 타입으로 접지 않는 이유: 접는 순간 무엇을 버렸는지 알 수 없어지고,
     * 나중에 단가를 계산할 때 버린 것을 요구한다. Provider 가 필드를 추가해도 그대로 들어온다.
     *
     * 그래서 이 값에는 **외부 필드명이 그대로** 들어 있다. 우리 포맷이 아니다.
     */
    val raw: Map<String, Any?> = emptyMap(),

    /**
     * Provider 가 **비용을 직접 답한** 경우만 채운다.
     *
     * 사용량으로부터 우리가 계산한 값은 여기 오지 않는다 — 그건 우리 추정이고,
     * 이 필드는 "Provider 가 그렇게 말했다" 는 실측이다. 둘을 섞으면 단가표를 고칠 때
     * 실측값까지 덮어쓰게 된다.
     */
    val reportedCost: ReportedCost? = null,
)

/** Provider 가 답한 비용. 단위가 값에 붙어 다녀야 합계가 뜻을 갖는다. */
data class ReportedCost(
    val amount: BigDecimal,
    val unit: CostUnit,
)

enum class CostUnit {
    /** 달러. 그대로 쓴다. */
    USD,

    /** Provider 자체 크레딧. 달러로 바꾸려면 그 Provider 의 크레딧 단가를 알아야 한다. */
    PROVIDER_CREDIT,
}
