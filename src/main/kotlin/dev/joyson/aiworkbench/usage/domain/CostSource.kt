package dev.joyson.aiworkbench.usage.domain

/**
 * [ProviderCallCost.usd] 가 어디서 나온 값인가.
 *
 * 신뢰 등급을 보여주려고 붙인 라벨이 아니다. **단가표를 고쳤을 때 어느 행을 다시 계산할지**
 * 가리는 필터다. 이것이 없으면 소급 재계산이 [REPORTED] 행까지 덮어써서
 * Provider 가 직접 말해준 실측값을 우리 추정값으로 바꿔 버린다.
 *
 * "모른다" 는 값을 두지 않는다 — [ProviderCallCost.usd] 가 null 인 것이 이미 그 뜻이다.
 * 같은 사실을 두 컬럼에 두면 어느 쪽이 정본인지 흐려진다.
 *
 * 이름을 basis 로 짓지 않는다. 비용을 다루는 자리에서 cost basis 는 취득원가라는 뜻이 이미 있다.
 */
enum class CostSource {
    /** Provider 가 비용을 직접 답했다. **절대 덮어쓰지 않는다.** */
    REPORTED,

    /**
     * Provider 가 준 사용량에 우리 단가표를 곱했다.
     *
     * 표가 고쳐지면 **이유를 묻지 않고** 다시 계산해도 되는 행이다. 계산이 호출 시각의 표를 쓰는 순수 함수라,
     * 새 기간의 단가는 과거 행의 값을 바꾸지 않고 우리가 틀렸던 것만 고쳐진다.
     */
    DERIVED,

    /** 사용량조차 없어 요청 파라미터로 추정했다. 다시 계산해도 여전히 추정이다. */
    ESTIMATED,
}
