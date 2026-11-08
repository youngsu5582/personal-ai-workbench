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

    /**
     * 사용량에 없으면 0 으로 보는 경로. 0 인 칸을 **빼고 보내는** Provider 를 위한 것이다.
     *
     * Google API 의 JSON 은 0 인 정수 칸을 싣지 않는다 — 생각하지 않은 호출엔 thinking 토큰 칸이 아예 없다.
     * 그 칸에 단가를 매기면서 "없으면 모른다" 를 지키면 그런 호출의 비용이 전부 모름이 된다.
     *
     * 표가 고르게 둔 이유는 **그 사실이 Provider 의 것**이라서다. 경로처럼 데이터에 있어야 계산하는 코드가 Provider 를 모른다.
     * 사용량에 0 을 채워 넣지 않는 이유는 `usage_raw` 가 저쪽이 보낸 원문이어야 청구서와 맞춰 볼 수 있어서다.
     *
     * 이 목록 밖의 경로는 여전히 엄격하다. 칸 이름이 바뀌면 여기 든 경로는 0 으로 조용히 계산되므로
     * 실제 응답에서 그 칸이 오는지 live 테스트로 확인한 경로만 넣는다.
     */
    val zeroIfAbsent: Set<String> = emptySet(),
) {
    init {
        require(usdPerMillion.isNotEmpty()) { "단가가 하나도 없는 표다" }
        require(usdPerMillion.values.all { it.signum() >= 0 }) { "단가는 음수일 수 없다: $usdPerMillion" }
        require(usdPerMillion.keys.containsAll(zeroIfAbsent)) {
            "단가가 없는 경로를 0 으로 볼 수는 없다: ${zeroIfAbsent - usdPerMillion.keys}"
        }
        // 모두 빠져도 0 이 되는 표는 빈 사용량에도 0 달러를 답한다 — 모르는 것을 공짜라고 말하게 된다.
        require(usdPerMillion.keys.any { it !in zeroIfAbsent }) { "적어도 한 경로는 사용량에 반드시 있어야 한다" }
    }

    /**
     * 사용량에 단가를 곱해 더한 USD.
     *
     * 표가 가리키는 경로 중 **하나라도 없으면 null** 이다([zeroIfAbsent] 에 든 경로는 빼고). 있는 것만 더하면 그 값이
     * 비용 칸에 들어가 "얼마인지 모른다" 가 "이만큼 썼다" 로 바뀐다 — 실제보다 싼 비용이 조용히 쌓인다.
     *
     * 반올림하지 않는다. 저장 눈금은 저장하는 쪽이 맞춘다.
     */
    fun price(usage: Map<String, Any?>): BigDecimal? {
        val perMillion = usdPerMillion.entries.fold(BigDecimal.ZERO) { total, (field, rate) ->
            val quantity = usage.quantityAt(field)
                ?: if (field in zeroIfAbsent && usage.isAbsentAt(field)) BigDecimal.ZERO else return null
            total + quantity * rate
        }
        return perMillion.movePointLeft(6)
    }

    /**
     * 경로 끝의 칸이 **없는** 것인가. 숫자가 아닌 값이 와 있는 것은 없는 것이 아니다 — 모양이 달라진 것이라 0 으로 보지 않는다.
     * 중간 단계가 통째로 없는 것은 없는 것으로 본다. 그 아래 칸이 모두 0 이면 Provider 가 묶음째 뺄 수 있다.
     */
    private fun Map<String, Any?>.isAbsentAt(path: String): Boolean {
        var node: Any? = this
        for (key in path.split('.')) {
            val map = node as? Map<*, *> ?: return node == null
            node = map[key] ?: return true
        }
        return false
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
