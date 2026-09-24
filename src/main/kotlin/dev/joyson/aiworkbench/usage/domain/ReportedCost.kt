package dev.joyson.aiworkbench.usage.domain

import dev.joyson.aiworkbench.usage.ReportedUnit
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.math.BigDecimal

/**
 * Provider 가 **비용을 직접 답한** 값. 토큰 수는 여기 오지 않는다 — 그건 사용량이지 비용이 아니다.
 *
 * 금액과 단위를 갈라 두지 않는 이유: **단위가 값에 붙어 다녀야 합계가 뜻을 갖는다.**
 * 따로 두면 크레딧 3 과 달러 0.19 가 같은 컬럼에 섞이는 것을 아무것도 막지 못한다.
 */
@Embeddable
class ReportedCost internal constructor(

    @Column(name = "reported_amount", precision = 18, scale = 8, updatable = false)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "reported_unit", length = 32, updatable = false)
    val unit: ReportedUnit,
) {

    /**
     * 달러로 답한 경우에만 금액이 나온다.
     *
     * 크레딧은 그 Provider 의 크레딧 단가를 알아야 달러가 되므로 여기서 답하지 않는다.
     * "달러면 그대로 비용" 이라는 규칙이 부르는 쪽에 흩어지지 않게 여기 둔다.
     */
    fun asUsd(): BigDecimal? = amount.takeIf { unit == ReportedUnit.USD }

    companion object {
        /** 둘 중 하나라도 없으면 없는 것으로 본다 — 단위 없는 금액은 뜻이 없다. */
        fun of(amount: BigDecimal?, unit: ReportedUnit?): ReportedCost? =
            if (amount != null && unit != null) ReportedCost(amount, unit) else null
    }
}
