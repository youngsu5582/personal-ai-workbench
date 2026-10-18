package dev.joyson.aiworkbench.usage

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.usage.application.ProviderCallPricingWorker
import dev.joyson.aiworkbench.usage.domain.CostSource
import dev.joyson.aiworkbench.usage.domain.ModelPriceBook
import dev.joyson.aiworkbench.usage.domain.PriceRates
import dev.joyson.aiworkbench.usage.domain.ProviderCallCost
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 쌓인 호출에 비용을 붙이는 규칙.
 *
 * 두 가지가 핵심이다. 하나는 **원장의 행 하나만으로 계산된다**는 것 — 사용량 원문 · 모델 · 호출 시각이 행 안에 있어야
 * 나중에 붙인 단가표로도 계산할 수 있다. 다른 하나는 **본 호출을 다시 보지 않는다**는 것 — 비용을 정하지 못한 호출이
 * 주기마다 다시 집히면 미등록 모델의 호출이 쌓일수록 새 호출이 밀린다.
 */
class ProviderCallPricingWorkerTest @Autowired constructor(
    private val recorder: ProviderCallRecorder,
    private val worker: ProviderCallPricingWorker,
    private val books: ModelPriceBookTestRepository,
    private val em: EntityManager,
) : IntegrationTest() {

    private val seeded = Instant.parse("2026-10-18T00:00:00Z")

    /** Images API 응답의 usage 모양이다. */
    private val usage = mapOf(
        "total_tokens" to 4200,
        "input_tokens" to 200,
        "output_tokens" to 4000,
        "input_tokens_details" to mapOf("text_tokens" to 150, "image_tokens" to 50),
    )

    private fun book(
        from: Instant = seeded,
        to: Instant? = null,
        rates: Map<String, BigDecimal> = mapOf(
            "input_tokens_details.text_tokens" to BigDecimal("5"),
            "input_tokens_details.image_tokens" to BigDecimal("8"),
            "output_tokens" to BigDecimal("30"),
        ),
    ) = books.save(
        ModelPriceBook(
            provider = "openai",
            model = "gpt-image-2",
            effectiveFrom = from,
            effectiveTo = to,
            sourceUrl = "https://example.com",
            rates = PriceRates(rates),
        ),
    )

    private fun record(
        model: String = "gpt-image-2",
        usageRaw: Map<String, Any?>? = usage,
        calledAt: Instant = Instant.parse("2026-10-20T00:00:00Z"),
        reportedAmount: BigDecimal? = null,
        reportedUnit: ReportedUnit? = null,
    ): UUID = recorder.record(
        RecordProviderCallCommand(
            taskUuid = UUID.randomUUID(),
            jobUuid = UUID.randomUUID(),
            ownerUserUuid = UUID.randomUUID(),
            provider = "openai",
            model = model,
            request = ImageRequest(width = 2048, height = 1152, quality = "high"),
            succeeded = true,
            latencyMs = 31_000,
            usageRaw = usageRaw,
            reportedAmount = reportedAmount,
            reportedUnit = reportedUnit,
            calledAt = calledAt,
        ),
    )

    /** 폴러가 한 주기에 하는 일. */
    private fun priceAll() = worker.unpriced(100).forEach(worker::price)

    private fun costOf(uuid: UUID): ProviderCallCost {
        em.flush()
        em.clear()
        return em
            .createQuery("select k from ProviderCallCost k where k.call.uuid = :uuid", ProviderCallCost::class.java)
            .setParameter("uuid", uuid)
            .singleResult
    }

    @Test
    fun `쌓아 둔 행 하나만으로 비용을 계산한다`() {
        val book = book()
        val uuid = record()
        // 영속성 컨텍스트를 비워, 계산이 메모리의 커맨드가 아니라 DB 의 행만 보게 한다.
        em.flush()
        em.clear()

        priceAll()

        val cost = costOf(uuid)
        // 150 × 5 + 50 × 8 + 4000 × 30 = 121,150 → 0.12115
        assertEquals(0, BigDecimal("0.12115").compareTo(cost.usd))
        assertEquals(CostSource.DERIVED, cost.source)
        assertEquals(book.id, cost.priceBook?.id)
    }

    @Test
    fun `호출 시각에 적용되던 표로 계산한다`() {
        val discountStart = Instant.parse("2026-11-01T00:00:00Z")
        val outputOnly = mapOf("output_tokens" to 1_000_000)
        book(to = discountStart, rates = mapOf("output_tokens" to BigDecimal("30")))
        book(from = discountStart, rates = mapOf("output_tokens" to BigDecimal("15")))

        val before = record(usageRaw = outputOnly, calledAt = discountStart.minusSeconds(1))
        val at = record(usageRaw = outputOnly, calledAt = discountStart)
        priceAll()

        assertEquals(0, BigDecimal("30").compareTo(costOf(before).usd))
        assertEquals(0, BigDecimal("15").compareTo(costOf(at).usd), "경계 시각의 호출에 옛 단가를 매겼다")
    }

    /** Provider 가 직접 말한 금액은 우리 계산보다 앞선다. 표가 있어도 그것으로 덮지 않는다. */
    @Test
    fun `Provider 가 달러로 답하면 그 금액이 비용이다`() {
        book()
        val uuid = record(reportedAmount = BigDecimal("0.19"), reportedUnit = ReportedUnit.USD)

        priceAll()

        val cost = costOf(uuid)
        assertEquals(CostSource.REPORTED, cost.source)
        assertEquals(0, BigDecimal("0.19").compareTo(cost.usd))
        assertNull(cost.priceBook, "받은 값인데 표로 계산한 것처럼 적었다")
    }

    /** 크레딧은 그 Provider 의 크레딧 단가를 알아야 달러가 된다. 지금은 모른다. */
    @Test
    fun `Provider 크레딧은 달러로 옮기지 않는다`() {
        val uuid = record(usageRaw = null, reportedAmount = BigDecimal("3"), reportedUnit = ReportedUnit.PROVIDER_CREDIT)

        priceAll()

        assertNull(costOf(uuid).usd, "크레딧을 달러로 착각했다")
    }

    @Test
    fun `비용을 정하지 못한 호출은 다시 집히지 않는다`() {
        book()
        val noBook = record(model = "unknown-model")
        val noUsage = record(usageRaw = null)
        val missingField = record(usageRaw = mapOf("output_tokens" to 4000))

        priceAll()

        for (uuid in listOf(noBook, noUsage, missingField)) {
            val cost = costOf(uuid)
            assertNull(cost.usd)
            assertNull(cost.source)
        }
        assertTrue(worker.unpriced(100).isEmpty(), "본 호출을 다시 집는다 — 미등록 모델이 쌓이면 새 호출이 밀린다")
    }

    /** 기간 겹침은 DB 가 막지 않는다. 그때 하나를 골라 적으면 틀린 단가가 실측처럼 남는다. */
    @Test
    fun `표가 겹치면 고르지 않는다`() {
        book()
        book(rates = mapOf("output_tokens" to BigDecimal("15")))
        val uuid = record()

        priceAll()

        assertNull(costOf(uuid).usd)
    }

    @Test
    fun `같은 호출을 두 번 넘겨받아도 비용 행은 하나다`() {
        book()
        record()
        val id = worker.unpriced(100).single()

        worker.price(id)
        worker.price(id)

        em.flush()
        val rows = em
            .createQuery("select count(k) from ProviderCallCost k where k.call.id = :id", Long::class.javaObjectType)
            .setParameter("id", id)
            .singleResult
        assertEquals(1L, rows)
    }

    @Test
    fun `오래된 호출부터 넘겨준다`() {
        val first = record()
        val second = record()
        val third = record()

        val ids = worker.unpriced(2)

        em.flush()
        em.clear()
        val uuids = ids.map {
            em.createQuery("select c.uuid from ProviderCall c where c.id = :id", UUID::class.java)
                .setParameter("id", it)
                .singleResult
        }
        assertEquals(listOf(first, second), uuids)
        assertTrue(third !in uuids)
    }
}
