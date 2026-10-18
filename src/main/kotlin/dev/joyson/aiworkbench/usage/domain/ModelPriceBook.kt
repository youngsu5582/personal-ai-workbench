package dev.joyson.aiworkbench.usage.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant

/**
 * 한 모델의 한 기간 동안의 단가표.
 *
 * **애플리케이션은 이 표를 쓰지 않는다.** 마이그레이션으로만 들어오고 바뀐다 —
 * 누가 어떤 근거로 바꿨는지가 PR 에 남아야 하기 때문이다. 그래서 모든 컬럼이 `updatable = false` 다.
 *
 * 단가는 [rates] 가 **`usage_raw` 안의 경로**에 매긴다. Provider 의 필드명이 코드가 아니라 데이터에 있어서,
 * 이 모듈은 OpenAI 를 모르는 채로 계산하고 Provider 를 들이는 일은 단가표를 더하는 것으로 끝난다.
 * 모델마다 표가 경로를 고르므로 "이 모델은 출력 토큰을 이렇게 센다" 같은 분기도 코드에 생기지 않는다.
 */
@Entity
@Table(
    name = "model_price_books",
    indexes = [
        Index(name = "idx_model_price_books_lookup", columnList = "provider, model, effective_from"),
    ],
)
class ModelPriceBook(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, updatable = false)
    val provider: String,

    @Column(nullable = false, updatable = false)
    val model: String,

    /** 이 시각의 호출부터 받는다. `[effectiveFrom, effectiveTo)` 다. */
    @Column(name = "effective_from", nullable = false, updatable = false)
    val effectiveFrom: Instant,

    /** 이 시각의 호출은 **받지 않는다** — 다음 표가 받는다. null 이면 지금 적용 중이다. */
    @Column(name = "effective_to", updatable = false)
    val effectiveTo: Instant? = null,

    @Column(name = "source_url", nullable = false, length = 1024, updatable = false)
    val sourceUrl: String,

    /**
     * jsonb 한 칸이다. 표 한 장이 늘 통째로 읽히고 그 안을 SQL 로 묻지 않으며, 경로의 종류가 모델마다 달라
     * 행으로 펼칠 이유가 없다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    val rates: PriceRates,
) {

    /** 사용량에 이 표의 단가를 곱해 더한 USD. 계산 규칙은 [PriceRates.price] 에 있다. */
    fun price(usage: Map<String, Any?>): BigDecimal? = rates.price(usage)
}
