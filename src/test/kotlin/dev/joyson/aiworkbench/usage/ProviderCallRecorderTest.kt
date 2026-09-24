package dev.joyson.aiworkbench.usage

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.usage.domain.CostBasis
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
        assertNull(loaded.cost)
        assertEquals(2048, assertIs<ImageRequest>(loaded.request).width)
        assertEquals(12_345, loaded.latencyMs)
    }

    /**
     * Provider 가 달러로 답하면 그것이 곧 비용이다. 계산이 아니라 같은 사실을 제자리에 옮기는 것이라
     * 단가표가 없는 지금도 채워진다. 이 행이 있어야 나중의 소급 계산이 "덮으면 안 되는 행" 을 가려낼 수 있다.
     */
    @Test
    fun `Provider 가 달러로 답하면 실측으로 적힌다`() {
        val loaded = reload(
            recorder.record(
                command(reportedAmount = BigDecimal("0.19000000"), reportedUnit = ReportedUnit.USD),
            ),
        )

        assertEquals(CostBasis.REPORTED, loaded.cost?.basis)
        assertEquals(0, BigDecimal("0.19").compareTo(loaded.cost?.usd))
    }

    /** 크레딧은 그 Provider 의 크레딧 단가를 알아야 달러가 된다. 지금은 모르므로 원문만 남긴다. */
    @Test
    fun `Provider 크레딧은 달러로 옮기지 않는다`() {
        val loaded = reload(
            recorder.record(
                command(reportedAmount = BigDecimal("3"), reportedUnit = ReportedUnit.PROVIDER_CREDIT),
            ),
        )

        assertEquals(ReportedUnit.PROVIDER_CREDIT, loaded.reported?.unit)
        assertNull(loaded.cost, "크레딧을 달러로 착각했다")
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
