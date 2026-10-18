package dev.joyson.aiworkbench.usage

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.usage.domain.ProviderCall
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 지출 기록의 성립 조건 두 가지를 본다.
 *
 * 하나는 **원자료가 무손실로 돌아오는가**다. Provider 응답은 한 번뿐이라 여기서 잃으면 끝이고,
 * 나중에 단가표를 붙여 소급 계산하려 할 때 없어진 필드를 요구하게 된다.
 *
 * 다른 하나는 **던지지 않는가**다. 이 INSERT 는 이미 돈을 쓴 뒤에 일어나므로,
 * 여기서 예외가 나면 부르는 쪽 작업이 실패 처리되고 재시도가 돈을 또 쓴다.
 */
class ProviderCallRecorderTest @Autowired constructor(
    private val recorder: ProviderCallRecorder,
    private val em: EntityManager,
) : IntegrationTest() {

    private fun command(
        usageRaw: Map<String, Any?>? = null,
        reportedAmount: BigDecimal? = null,
        reportedUnit: ReportedUnit? = null,
        model: String = "gpt-image-2",
        failureReason: String? = null,
    ) = RecordProviderCallCommand(
        taskUuid = UUID.randomUUID(),
        jobUuid = UUID.randomUUID(),
        ownerUserUuid = UUID.randomUUID(),
        provider = "openai",
        model = model,
        request = ImageRequest(width = 2048, height = 1152, quality = "high"),
        succeeded = failureReason == null,
        failureReason = failureReason,
        latencyMs = 12_345,
        usageRaw = usageRaw,
        reportedAmount = reportedAmount,
        reportedUnit = reportedUnit,
    )

    /** 저장 후 영속성 컨텍스트를 비워 **진짜로 DB 를 다녀오게** 한다. 안 비우면 1차 캐시가 답해 왕복이 안 된다. */
    private fun reload(uuid: UUID): ProviderCall {
        em.flush()
        em.clear()
        return em
            .createQuery("select c from ProviderCall c where c.uuid = :uuid", ProviderCall::class.java)
            .setParameter("uuid", uuid)
            .singleResult
    }

    @Test
    fun `중첩된 사용량 원문이 그대로 돌아온다`() {
        val raw = mapOf(
            "total_tokens" to 4200,
            "input_tokens" to 200,
            "output_tokens" to 4000,
            "input_tokens_details" to mapOf("text_tokens" to 150, "image_tokens" to 50),
        )

        val loaded = reload(recorder.record(command(usageRaw = raw)))

        val stored = assertNotNull(loaded.usageRaw)
        assertEquals("4200", stored["total_tokens"].toString())
        // 우리가 모르는 모양이 와도 통째로 보존되어야 한다 — 그게 이 컬럼의 존재 이유다.
        val details = assertIs<Map<*, *>>(stored["input_tokens_details"], "중첩 구조가 평평해졌다")
        assertEquals("150", details["text_tokens"].toString())
        assertEquals("50", details["image_tokens"].toString())
    }

    @Test
    fun `아무것도 말하지 않는 Provider 도 기록된다`() {
        val loaded = reload(recorder.record(command(usageRaw = null)))

        assertNull(loaded.usageRaw)
        val request = assertIs<ImageRequest>(loaded.request, "종류를 잃었다")
        // 토큰을 주지 않는 Provider 에서는 실제로 보낸 크기·품질이 유일한 계산 근거다. Job 어디에도 없는 값이다.
        assertEquals(2048, request.width)
        assertEquals(1152, request.height)
        assertEquals("high", request.quality)
        assertEquals(12_345, loaded.latencyMs)
    }

    /**
     * Provider 가 답한 금액은 단위와 함께 사실로 남는다. 그것을 비용으로 옮기는 일은 여기서 하지 않는다 —
     * 비용 행이 있는지가 "그 호출을 봤는가" 의 표시라 한 경로로만 만들어진다.
     */
    @Test
    fun `Provider 가 답한 금액은 단위와 함께 적힌다`() {
        val usd = reload(
            recorder.record(
                command(reportedAmount = BigDecimal("0.19000000"), reportedUnit = ReportedUnit.USD),
            ),
        )
        val credit = reload(
            recorder.record(
                command(reportedAmount = BigDecimal("3"), reportedUnit = ReportedUnit.PROVIDER_CREDIT),
            ),
        )

        assertEquals(0, BigDecimal("0.19").compareTo(usd.reported?.amount))
        assertEquals(ReportedUnit.USD, usd.reported?.unit)
        assertEquals(ReportedUnit.PROVIDER_CREDIT, credit.reported?.unit)
    }

    /**
     * 사유가 길다는 이유로 기록을 통째로 잃으면 안 된다.
     * Provider 실패 메시지에는 응답 본문이 실려 오기도 해서 컬럼 폭을 넘기기 쉽다.
     */
    @Test
    fun `컬럼보다 긴 값이 와도 잘라서 적고 던지지 않는다`() {
        val long = "실".repeat(500)

        val loaded = reload(recorder.record(command(failureReason = long, model = "모".repeat(400))))

        assertEquals(255, loaded.failureReason!!.length)
        assertEquals(255, loaded.model.length)
    }
}
