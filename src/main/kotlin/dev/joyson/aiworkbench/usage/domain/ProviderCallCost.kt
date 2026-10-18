package dev.joyson.aiworkbench.usage.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * 호출 하나의 비용. [ProviderCall] 이라는 사실에 대한 **우리의 해석**이다.
 *
 * 사실과 따로 사는 이유는 수명이 달라서다. 토큰 수는 Provider 가 정해 바뀌지 않지만,
 * 비용은 단가표가 고쳐지면 다시 계산된다. 같은 행에 두면 불변인 원장에 바뀌는 칸이 생긴다.
 *
 * **행이 있으면 본 호출이다.** 봤지만 비용을 정하지 못했으면 [usd] 가 null 인 행이 남는다.
 * 그래서 계산하는 쪽은 "이 호출을 다시 볼까" 를 이 행의 존재로 판단한다.
 *
 * [usd] · [source] · [priceBook] 은 **늘 함께 정해진다.** [source] 가 빠진 금액은 나중의 재계산이
 * [CostSource.REPORTED] 행까지 덮어써서 실측값을 우리 추정으로 바꾼다. 그래서 출처별 팩토리만 연다.
 *
 * 생성자가 `internal` 인 이유는 [ReportedCost] 와 같다.
 */
@Entity
@Table(name = "provider_call_costs")
class ProviderCallCost internal constructor(

    /** 호출과 키를 공유한다. 같은 호출에 비용 행이 둘 생기면 두 번째 INSERT 가 PK 에 막힌다. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "provider_call_id")
    val call: ProviderCall,

    /** null 이 곧 "봤지만 모른다" 다. 0 은 "안 썼다" 라는 뜻이라 대신 쓸 수 없다. */
    @Column(precision = 18, scale = 8, updatable = false)
    val usd: BigDecimal?,

    @Enumerated(EnumType.STRING)
    @Column(length = 32, updatable = false)
    val source: CostSource?,

    /** 어느 표로 계산했나. Provider 가 달러로 답한 [CostSource.REPORTED] 에는 없다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_book_id", updatable = false)
    val priceBook: ModelPriceBook?,

    @Column(name = "priced_at", nullable = false, updatable = false)
    val pricedAt: Instant,
) {

    @Id
    @Column(name = "provider_call_id")
    val providerCallId: Long? = null

    companion object {
        /** Provider 가 직접 답했다. 재계산이 **절대 덮으면 안 되는** 행이다. */
        fun reported(call: ProviderCall, usd: BigDecimal, pricedAt: Instant) =
            ProviderCallCost(call, usd, CostSource.REPORTED, priceBook = null, pricedAt = pricedAt)

        /** Provider 가 준 사용량에 우리 단가표를 곱했다. */
        fun derived(call: ProviderCall, usd: BigDecimal, priceBook: ModelPriceBook, pricedAt: Instant) =
            ProviderCallCost(call, usd, CostSource.DERIVED, priceBook, pricedAt)

        /** 봤지만 정하지 못했다. 맞는 표가 없거나, 사용량이 없거나, 표가 가리키는 칸이 없다. */
        fun unknown(call: ProviderCall, pricedAt: Instant) =
            ProviderCallCost(call, usd = null, source = null, priceBook = null, pricedAt = pricedAt)
    }
}
