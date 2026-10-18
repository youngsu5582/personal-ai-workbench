package dev.joyson.aiworkbench.usage

import dev.joyson.aiworkbench.usage.domain.PriceRates
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 단가표 한 장이 사용량을 금액으로 바꾸는 규칙.
 *
 * 가장 중요한 것은 **모르는 것을 싸게 말하지 않는 것**이다. 경로 하나가 빠졌을 때 있는 것만 더하면
 * 그럴듯한 금액이 비용 칸에 들어가고, 그 값은 실제보다 작다.
 */
class PriceRatesTest {

    /** gpt-image-2 의 표와 같은 모양이다. 단가는 V10 과 같다. */
    private val rates = PriceRates(
        mapOf(
            "input_tokens_details.text_tokens" to BigDecimal("5"),
            "input_tokens_details.image_tokens" to BigDecimal("8"),
            "output_tokens" to BigDecimal("30"),
        ),
    )

    @Test
    fun `경로마다 단가를 곱해 더한다`() {
        val usage = mapOf(
            "input_tokens" to 200,
            "output_tokens" to 4000,
            "input_tokens_details" to mapOf("text_tokens" to 150, "image_tokens" to 50),
        )

        // 150 × 5 + 50 × 8 + 4000 × 30 = 121,150 → 1M 으로 나누면 0.12115
        assertEquals(0, BigDecimal("0.12115").compareTo(rates.price(usage)))
    }

    /** 표에 없는 칸(`input_tokens`, `total_tokens`)은 곱하지 않는다. 합계 칸까지 곱하면 두 번 센다. */
    @Test
    fun `표에 없는 칸은 비용에 들어가지 않는다`() {
        val usage = mapOf(
            "total_tokens" to 1_000_000,
            "input_tokens" to 1_000_000,
            "output_tokens" to 0,
            "input_tokens_details" to mapOf("text_tokens" to 0, "image_tokens" to 0),
        )

        assertEquals(0, BigDecimal.ZERO.compareTo(rates.price(usage)))
    }

    @Test
    fun `경로가 하나라도 없으면 0 이 아니라 모른다`() {
        val withoutOutput = mapOf(
            "input_tokens_details" to mapOf("text_tokens" to 150, "image_tokens" to 50),
        )

        assertNull(rates.price(withoutOutput), "있는 것만 더했다 — 실제보다 싼 비용이 적힌다")
    }

    @Test
    fun `중간이 맵이 아니거나 끝이 숫자가 아니면 없는 것으로 본다`() {
        assertNull(rates.price(mapOf("input_tokens_details" to 150, "output_tokens" to 10)))
        assertNull(
            rates.price(
                mapOf(
                    "input_tokens_details" to mapOf("text_tokens" to "150", "image_tokens" to 0),
                    "output_tokens" to 10,
                ),
            ),
        )
    }

    /** "모른다" 도 "0 달러" 도 아니라 잘못 넣은 표다. jsonb 에는 DB 제약이 없어 생성자가 막는다. */
    @Test
    fun `단가가 없거나 음수인 표는 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { PriceRates(emptyMap()) }
        assertFailsWith<IllegalArgumentException> { PriceRates(mapOf("output_tokens" to BigDecimal("-30"))) }
    }

    /**
     * 호출 단위에서 반올림하면 안 되는 이유.
     *
     * 센트 미만의 호출을 센트로 접으면 0 이 되고, 그런 행을 아무리 더해도 0 이다.
     * 합계가 조용히 거짓말하므로 눈금은 계산이 아니라 보여주는 자리에서 맞춘다.
     */
    @Test
    fun `센트 미만의 비용도 0 으로 접히지 않는다`() {
        val textOnly = PriceRates(mapOf("input_tokens_details.text_tokens" to BigDecimal("5")))

        val perCall = assertNotNull(textOnly.price(mapOf("input_tokens_details" to mapOf("text_tokens" to 19))))
        assertEquals(0, BigDecimal("0.000095").compareTo(perCall))

        val thousand = (1..1000).fold(BigDecimal.ZERO) { acc, _ -> acc + perCall }
        assertEquals(0, BigDecimal("0.095").compareTo(thousand))

        // 같은 값을 센트로 먼저 접었다면 전부 사라졌을 것이다.
        assertTrue(perCall.setScale(2, RoundingMode.HALF_UP).signum() == 0)
    }
}
