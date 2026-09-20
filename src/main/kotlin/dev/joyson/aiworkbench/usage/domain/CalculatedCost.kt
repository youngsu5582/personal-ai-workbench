package dev.joyson.aiworkbench.usage.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.math.BigDecimal

/**
 * 달러로 환산한 비용과 **그것이 어디서 나왔는지**.
 *
 * 셋을 따로 두지 않는 이유는 하나만 채워지는 것을 막기 위해서다.
 * [basis] 가 빠진 행은 나중에 소급 재계산이 [CostBasis.REPORTED] 행까지 덮어써서
 * **실측값을 우리 추정값으로 바꾼다.** 금액만 세팅되는 경로가 있으면 언젠가 그 일이 일어난다.
 *
 * 그래서 기준별 팩토리를 연다. 금액을 고치려면 **덩어리째** 바꿔야 한다.
 *
 * 생성자를 `private` 이 아니라 `internal` 로 둔다. kotlin-jpa 가 만들어 주는 no-arg 생성자가
 * 선언한 가시성을 따라가서, private 이면 Hibernate 가 `InstantiationException` 으로 못 만든다.
 * 이 모듈의 첫 `@Embeddable` 이라 여기 적어 둔다.
 *
 * 가려서 얻으려던 것은 대부분 시그니처가 이미 지킨다 — 필수 인자가 둘이라
 * 금액만 세팅되는 생성 자체가 불가능하다.
 */
@Embeddable
class CalculatedCost internal constructor(

    /**
     * **반올림하지 않는다.** 호출 하나가 $0.00019 인데 센트로 접으면 0 이 되고,
     * 그런 행을 백 개 더해도 0 이다. 조용하고 치명적이라 눈금은 표시 경계에서 맞춘다.
     */
    @Column(name = "cost_usd", precision = 18, scale = 8)
    val usd: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_basis", length = 32)
    val basis: CostBasis,

    /**
     * 어느 단가표로 계산했나. 표 자체의 이력은 git 이 갖고, 이 값이 행과 그 이력을 잇는다.
     *
     * [CostBasis.REPORTED] 에는 없다 — 계산한 것이 아니라 받은 값이라 단가표가 관여하지 않는다.
     */
    @Column(name = "price_book_version", length = 32)
    val priceBookVersion: String? = null,
) {

    companion object {
        /** Provider 가 직접 답했다. 소급 재계산이 **절대 덮으면 안 되는** 행이다. */
        fun reported(usd: BigDecimal) = CalculatedCost(usd, CostBasis.REPORTED)

        /** Provider 가 준 사용량에 우리 단가표를 곱했다. 표가 바뀌면 이유를 묻지 않고 다시 계산된다. */
        fun derived(usd: BigDecimal, priceBookVersion: String) =
            CalculatedCost(usd, CostBasis.DERIVED, priceBookVersion)

        /** 사용량조차 없어 요청 파라미터로 추정했다. 다시 계산해도 여전히 추정이다. */
        fun estimated(usd: BigDecimal, priceBookVersion: String) =
            CalculatedCost(usd, CostBasis.ESTIMATED, priceBookVersion)
    }
}
