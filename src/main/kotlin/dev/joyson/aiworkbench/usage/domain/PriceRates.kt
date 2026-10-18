package dev.joyson.aiworkbench.usage.domain

import java.math.BigDecimal

/**
 * 단가표 한 장의 단가. [usdPerMillion] 은 `usage_raw` 안의 경로 → 1M 당 USD 다.
 *
 * [ModelPriceBook] 의 jsonb 한 칸으로 저장된다 — `{"usdPerMillion": {"output_tokens": 30, …}}`.
 * 맵을 바로 두지 않고 감싸는 이유는 표 전체에 걸리는 칸(통화 등)이 생겼을 때 이 객체에 더해 옛 행을 그대로 읽기 위해서다.
 * 필드 이름이 JSON 에 그대로 박히므로 리네임하면 옛 행을 못 읽는다.
 *
 * **생성자가 불변식을 지킨다.** jsonb 안의 값에는 DB 제약이 없어서, DB 에서 읽을 때도 이 검사를 거친다.
 * 단가가 없거나 음수인 표는 "모른다" 도 "0 달러" 도 아니라 잘못 넣은 표다.
 */
data class PriceRates(
    val usdPerMillion: Map<String, BigDecimal>,
) {
    init {
        require(usdPerMillion.isNotEmpty()) { "단가가 하나도 없는 표다" }
        require(usdPerMillion.values.all { it.signum() >= 0 }) { "단가는 음수일 수 없다: $usdPerMillion" }
    }

    /**
     * 사용량에 단가를 곱해 더한 USD.
     *
     * 표가 가리키는 경로 중 **하나라도 없으면 null** 이다. 있는 것만 더하면 그 값이 비용 칸에 들어가
     * "얼마인지 모른다" 가 "이만큼 썼다" 로 바뀐다 — 실제보다 싼 비용이 조용히 쌓인다.
     *
     * 반올림하지 않는다. 저장 눈금은 저장하는 쪽이 맞춘다.
     */
    fun price(usage: Map<String, Any?>): BigDecimal? {
        val perMillion = usdPerMillion.entries.fold(BigDecimal.ZERO) { total, (field, rate) ->
            val quantity = usage.quantityAt(field) ?: return null
            total + quantity * rate
        }
        return perMillion.movePointLeft(6)
    }

    /** 점으로 가른 경로를 따라 내려가 숫자를 꺼낸다. 중간이 맵이 아니거나 끝이 숫자가 아니면 없는 것이다. */
    private fun Map<String, Any?>.quantityAt(path: String): BigDecimal? {
        var node: Any? = this
        for (key in path.split('.')) {
            node = (node as? Map<*, *>)?.get(key) ?: return null
        }
        return (node as? Number)?.toString()?.toBigDecimalOrNull()
    }
}
