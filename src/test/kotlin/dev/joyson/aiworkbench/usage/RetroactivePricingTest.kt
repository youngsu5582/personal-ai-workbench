package dev.joyson.aiworkbench.usage

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.usage.domain.ProviderCall
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **이 단계의 완료 기준이다.**
 *
 * 지금은 단가표가 없어 `costUsd` 가 비어 있다. 그래도 이 단계가 혼자 쓸모 있으려면
 * 나중에 단가표를 붙였을 때 **이미 쌓인 행을 소급해 계산할 수 있어야** 한다.
 * 그러려면 계산에 필요한 입력이 전부 행 안에 있어야 한다 —
 * 사용량 원문, 모델, 호출 시각(단가는 시간에 따라 바뀐다), 그리고 실제로 보낸 크기·품질.
 *
 * 하나라도 빠지면 이 테이블은 "나중에 못 쓰는 로그" 가 된다.
 * 그 사실은 몇 달치를 쌓은 뒤에야 드러나고, 그때는 되돌릴 방법이 없다.
 *
 * 여기서 쓰는 단가는 예시다. 실제 표는 다음 단계가 갖는다 —
 * 이 테스트가 고정하는 것은 단가가 아니라 **행의 자족성**이다.
 */
class RetroactivePricingTest @Autowired constructor(
    private val recorder: ProviderCallRecorder,
    private val em: EntityManager,
) : IntegrationTest() {

    @Test
    fun `쌓아 둔 행 하나만으로 나중에 비용을 계산할 수 있다`() {
        val calledAt = Instant.parse("2026-09-20T00:00:00Z")
        val uuid = recorder.record(
            RecordProviderCallCommand(
                taskUuid = UUID.randomUUID(),
                jobUuid = UUID.randomUUID(),
                ownerUserUuid = UUID.randomUUID(),
                provider = "openai",
                model = "gpt-image-2",
                request = ImageRequest(width = 2048, height = 1152, quality = "high"),
                succeeded = true,
                latencyMs = 31_000,
                usageRaw = mapOf(
                    "input_tokens" to 200,
                    "output_tokens" to 4000,
                    "input_tokens_details" to mapOf("text_tokens" to 150, "image_tokens" to 50),
                ),
                calledAt = calledAt,
            ),
        )

        em.flush()
        em.clear()
        val row = em
            .createQuery("select c from ProviderCall c where c.uuid = :uuid", ProviderCall::class.java)
            .setParameter("uuid", uuid)
            .singleResult

        // ① 어느 단가표를 고를지 — 모델과 시각이 있어야 정해진다.
        assertEquals("gpt-image-2", row.model)
        assertEquals(calledAt, row.calledAt)

        // ② 토큰 단가를 쓰는 Provider — 원문에서 항목별로 꺼낼 수 있어야 한다.
        //    합계 하나만 남겼다면 input 과 output 의 단가가 달라 곱할 수가 없다.
        val raw = assertNotNull(row.usageRaw)
        val inputTokens = raw["input_tokens"].toString().toBigDecimal()
        val outputTokens = raw["output_tokens"].toString().toBigDecimal()

        val perMillion = BigDecimal("1000000")
        val cost = inputTokens.multiply(BigDecimal("5.00")).divide(perMillion, 8, RoundingMode.HALF_UP) +
            outputTokens.multiply(BigDecimal("40.00")).divide(perMillion, 8, RoundingMode.HALF_UP)

        assertEquals(0, BigDecimal("0.16100000").compareTo(cost))

        // ③ 토큰을 주지 않는 Provider 를 위해 — 실제로 보낸 크기·품질도 있어야 한다.
        //    이 값은 Job 어디에도 없다. 여기 없으면 되살릴 수 없다.
        val request = assertIs<ImageRequest>(row.request, "종류를 잃었다")
        assertEquals(2048, request.width)
        assertEquals(1152, request.height)
        assertEquals("high", request.quality)
    }

    /**
     * 반올림을 저장 시점에 하면 안 되는 이유.
     *
     * 호출 하나가 센트 미만이면 센트로 접는 순간 0 이 되고, 그런 행을 아무리 더해도 0 이다.
     * 합계가 조용히 거짓말하므로 눈금은 컬럼이 아니라 보여주는 자리에서 맞춘다.
     */
    @Test
    fun `센트 미만의 호출도 더하면 뜻 있는 금액이 된다`() {
        val perCall = BigDecimal("19").multiply(BigDecimal("5.00"))
            .divide(BigDecimal("1000000"), 8, RoundingMode.HALF_UP)

        assertTrue(perCall > BigDecimal.ZERO, "호출당 비용이 0 으로 접혔다")
        assertEquals(0, BigDecimal("0.00009500").compareTo(perCall))

        val thousand = (1..1000).fold(BigDecimal.ZERO) { acc, _ -> acc + perCall }
        assertEquals(0, BigDecimal("0.095").compareTo(thousand))

        // 같은 값을 센트로 먼저 접었다면 전부 사라졌을 것이다.
        val roundedFirst = perCall.setScale(2, RoundingMode.HALF_UP)
        assertEquals(0, BigDecimal.ZERO.compareTo(roundedFirst))
    }
}
