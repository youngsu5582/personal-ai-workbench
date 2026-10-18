package dev.joyson.aiworkbench.usage

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.usage.domain.ModelPriceBook
import dev.joyson.aiworkbench.usage.domain.PriceRates
import dev.joyson.aiworkbench.usage.domain.ModelPriceBookRepository
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * 호출 시각에 맞는 표를 고르는 규칙. 단가가 바뀌는 경계에서 두 표가 함께 걸리거나 둘 다 빠지면
 * 그 시각의 호출이 두 번 계산되거나 영영 계산되지 않는다.
 */
class ModelPriceBookRepositoryTest @Autowired constructor(
    private val repository: ModelPriceBookRepository,
    private val books: ModelPriceBookTestRepository,
    private val em: EntityManager,
) : IntegrationTest() {

    private val discountStart = Instant.parse("2026-11-01T00:00:00Z")

    private fun book(from: Instant, to: Instant?, outputPrice: String) = books.save(
        ModelPriceBook(
            provider = "openai",
            model = "gpt-image-2",
            effectiveFrom = from,
            effectiveTo = to,
            sourceUrl = "https://example.com",
            rates = PriceRates(mapOf("output_tokens" to BigDecimal(outputPrice))),
        ),
    )

    private fun effectiveAt(at: Instant) =
        repository.findEffective("openai", "gpt-image-2", at)

    @Test
    fun `경계 시각의 호출은 새 표가 받는다`() {
        val before = book(Instant.parse("2026-10-18T00:00:00Z"), discountStart, "30")
        val discount = book(discountStart, null, "15")
        em.flush()
        em.clear()

        assertEquals(listOf(before.id), effectiveAt(discountStart.minusNanos(1_000)).map { it.id })
        assertEquals(listOf(discount.id), effectiveAt(discountStart).map { it.id })
    }

    @Test
    fun `적용 시작 전의 호출에는 표가 없다`() {
        book(Instant.parse("2026-10-18T00:00:00Z"), null, "30")
        em.flush()
        em.clear()

        assertTrue(effectiveAt(Instant.parse("2026-10-17T23:59:59Z")).isEmpty())
    }

    /** 경로와 단가가 표와 함께 돌아와야 한다. 값이 소수 8자리에서 잘리면 싼 단가가 0 이 된다. */
    @Test
    fun `단가가 눈금을 잃지 않고 돌아온다`() {
        book(Instant.parse("2026-10-18T00:00:00Z"), null, "0.62500000")
        em.flush()
        em.clear()

        val loaded = effectiveAt(Instant.parse("2026-10-20T00:00:00Z")).single()
        assertEquals(0, BigDecimal("0.625").compareTo(loaded.rates.usdPerMillion["output_tokens"]))
    }

    @Test
    fun `다른 모델의 표는 고르지 않는다`() {
        books.save(
            ModelPriceBook(
                provider = "openai",
                model = "gpt-image-1",
                effectiveFrom = Instant.parse("2026-10-18T00:00:00Z"),
                sourceUrl = "https://example.com",
                rates = PriceRates(mapOf("output_tokens" to BigDecimal("40"))),
            ),
        )
        em.flush()
        em.clear()

        assertTrue(effectiveAt(Instant.parse("2026-10-20T00:00:00Z")).isEmpty())
    }

    /**
     * jsonb 안의 값에는 DB 제약이 없다. 마이그레이션이 음수 단가를 넣어도 INSERT 는 성공한다 —
     * 그 표가 계산에 쓰이지 않는 것은 읽을 때 [PriceRates] 의 생성자를 거치기 때문이어야 한다.
     */
    @Test
    fun `DB 에 잘못 들어간 표는 읽는 순간 거절된다`() {
        em.createNativeQuery(
            """
            insert into model_price_books (provider, model, effective_from, source_url, rates)
            values ('openai', 'gpt-image-2', timestamp with time zone '2026-10-18 00:00:00+00', 'https://example.com',
                    '{"usdPerMillion": {"output_tokens": -30}}')
            """,
        ).executeUpdate()
        em.flush()
        em.clear()

        val failure = assertFails("음수 단가 표가 그대로 읽혔다") { effectiveAt(Instant.parse("2026-10-20T00:00:00Z")) }

        // 매핑 오류 같은 다른 이유로 실패한 것이 아니라 생성자의 불변식에 걸린 것이어야 한다.
        val causes = generateSequence(failure as Throwable) { it.cause }.toList()
        assertTrue(causes.any { it.message.orEmpty().contains("음수일 수 없다") }, "다른 이유로 실패했다: $causes")
    }
}
