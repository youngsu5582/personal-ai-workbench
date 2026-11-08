package dev.joyson.aiworkbench.usage

import dev.joyson.aiworkbench.TestcontainersConfiguration
import dev.joyson.aiworkbench.provider.gemini.GeminiTextModel
import dev.joyson.aiworkbench.provider.openai.OpenAIImageModel
import dev.joyson.aiworkbench.provider.openai.OpenAITextModel
import dev.joyson.aiworkbench.usage.domain.ModelPriceBook
import dev.joyson.aiworkbench.usage.domain.ModelPriceBookRepository
import dev.joyson.aiworkbench.usage.domain.PriceRates
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 마이그레이션이 넣은 단가표를 **실제로 걸린** DB 에서 읽어 본다.
 *
 * 경로 철자가 하나만 틀려도 그 모델의 호출은 전부 "비용을 모른다" 가 된다. 예외도 없고 로그 한 줄뿐이라
 * 배포 뒤에야 알게 된다. 그래서 응답 모양의 사용량을 계산해 본다.
 *
 * 단가는 jsonb 한 칸이라 DB 제약이 값을 보지 않는다. 빈 표 · 음수 단가는 읽는 순간 [PriceRates] 의 생성자가 막으므로,
 * 마이그레이션이 넣은 모든 표를 읽어 보는 것이 그 검사를 CI 에서 돌리는 길이다.
 *
 * 설정이 [dev.joyson.aiworkbench.SchemaMigrationTest] 와 같다. 같아야 그 컨텍스트를 함께 쓴다.
 */
@ActiveProfiles("test")
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "logging.level.org.flywaydb=INFO",
    ],
)
@Import(TestcontainersConfiguration::class)
@Transactional
class PriceBookSeedTest @Autowired constructor(
    private val repository: ModelPriceBookRepository,
    private val em: EntityManager,
) {

    /** Images API 응답의 usage 모양이다. 이미지 입력 토큰은 edits 에서만 0 이 아니다. */
    private val usage = mapOf(
        "total_tokens" to 4200,
        "input_tokens" to 200,
        "output_tokens" to 4000,
        "input_tokens_details" to mapOf("text_tokens" to 150, "image_tokens" to 50),
    )

    private val afterSeed = Instant.parse("2026-10-20T00:00:00Z")

    private fun priceOf(model: String): BigDecimal? =
        repository.findEffective("openai", model, afterSeed).single().price(usage)

    @Test
    fun `gpt-image-2 계열은 텍스트 5 · 이미지 입력 8 · 출력 30 이다`() {
        // 150 × 5 + 50 × 8 + 4000 × 30 = 121,150
        for (model in listOf("gpt-image-2", "gpt-image-2.5-sunburst", "gpt-image-2.5-flare")) {
            assertEquals(0, BigDecimal("0.12115").compareTo(priceOf(model)), model)
        }
    }

    @Test
    fun `gpt-image-1 은 텍스트 5 · 이미지 입력 10 · 출력 40 이다`() {
        // 150 × 5 + 50 × 10 + 4000 × 40 = 161,250
        assertEquals(0, BigDecimal("0.16125").compareTo(priceOf("gpt-image-1")))
    }

    /** Responses API 응답의 usage 모양이다. 추론 토큰과 캐시 토큰은 각각 output · input 의 내역이다. */
    private val textUsage = mapOf(
        "input_tokens" to 2000,
        "input_tokens_details" to mapOf("cached_tokens" to 0),
        "output_tokens" to 1000,
        "output_tokens_details" to mapOf("reasoning_tokens" to 400),
        "total_tokens" to 3000,
    )

    private fun textPriceOf(model: String): BigDecimal? =
        repository.findEffective("openai", model, afterSeed).single().price(textUsage)

    @Test
    fun `텍스트 모델은 입력과 출력 토큰에 매긴다`() {
        // 2000 × 10 + 1000 × 50 = 70,000
        assertEquals(0, BigDecimal("0.07").compareTo(textPriceOf("gpt-6-astra")))
        // 2000 × 2 + 1000 × 10 = 14,000
        assertEquals(0, BigDecimal("0.014").compareTo(textPriceOf("gpt-6.1-sol")))
        // 2000 × 0.10 + 1000 × 0.50 = 700
        assertEquals(0, BigDecimal("0.0007").compareTo(textPriceOf("gpt-6-luna")))
        // 2000 × 2 + 1000 × 12 = 16,000
        assertEquals(0, BigDecimal("0.016").compareTo(textPriceOf("gpt-5.6-terra")))
    }

    /** 단가표가 없는 모델의 호출은 전부 "비용을 모른다" 가 된다. 모델을 더할 때 표를 빠뜨리면 여기서 걸린다. */
    @Test
    fun `어댑터가 다루는 OpenAI 모델은 모두 단가표가 있다`() {
        val models = OpenAIImageModel.entries.map { it.modelName } + OpenAITextModel.entries.map { it.modelName }

        val missing = models.filter { repository.findEffective("openai", it, afterSeed).isEmpty() }

        assertTrue(missing.isEmpty(), "단가표가 없는 모델: $missing")
    }

    /** generateContent 응답의 usageMetadata 모양이다. 답과 생각은 따로 센다. */
    private val geminiUsage = mapOf(
        "promptTokenCount" to 2000,
        "candidatesTokenCount" to 600,
        "thoughtsTokenCount" to 400,
        "totalTokenCount" to 3000,
        "promptTokensDetails" to listOf(mapOf("modality" to "TEXT", "tokenCount" to 2000)),
    )

    private val afterGeminiSeed = Instant.parse("2026-11-10T00:00:00Z")

    private fun geminiPriceOf(model: String, at: Instant = afterGeminiSeed, usage: Map<String, Any?> = geminiUsage): BigDecimal? =
        repository.findEffective("gemini", model, at).single().price(usage)

    @Test
    fun `Gemini 는 답과 생각에 같은 출력 단가를 매긴다`() {
        // 2000 × 0.75 + (600 + 400) × 3.75 = 5,250
        assertEquals(0, BigDecimal("0.00525").compareTo(geminiPriceOf("gemini-3.6-flash")))
        // 2000 × 0.30 + (600 + 400) × 2.50 = 3,100
        assertEquals(0, BigDecimal("0.0031").compareTo(geminiPriceOf("gemini-3.5-flash-lite")))
    }

    /** 발표된 인상을 미리 넣어 두었다. 비용은 호출 시각으로 표를 고르므로 그날 할 일이 없다. */
    @Test
    fun `Gemini Flash 는 2027년부터 단가가 두 배다`() {
        val lastCall = Instant.parse("2026-12-31T23:59:59Z")
        val firstCall = Instant.parse("2027-01-01T00:00:00Z")

        assertEquals(0, BigDecimal("0.00525").compareTo(geminiPriceOf("gemini-3.6-flash", at = lastCall)))
        // 2000 × 1.50 + 1000 × 7.50 = 10,500
        assertEquals(0, BigDecimal("0.0105").compareTo(geminiPriceOf("gemini-3.6-flash", at = firstCall)))
    }

    /** 0 인 칸은 저쪽이 빼고 보낸다. 표가 그 칸을 0 으로 보지 않으면 생각하지 않은 호출의 비용을 모르게 된다. */
    @Test
    fun `생각하지 않은 Gemini 호출은 thinking 칸이 없어도 비용을 안다`() {
        val noThinking = mapOf("promptTokenCount" to 2000, "candidatesTokenCount" to 600, "totalTokenCount" to 2600)

        // 2000 × 0.30 + 600 × 2.50 = 2,100
        assertEquals(0, BigDecimal("0.0021").compareTo(geminiPriceOf("gemini-3.5-flash-lite", usage = noThinking)))
    }

    @Test
    fun `어댑터가 다루는 Gemini 모델은 모두 단가표가 있다`() {
        val missing = GeminiTextModel.entries.map { it.modelName }
            .filter { repository.findEffective("gemini", it, afterGeminiSeed).isEmpty() }

        assertTrue(missing.isEmpty(), "단가표가 없는 모델: $missing")
    }

    @Test
    fun `적용 시작 전의 호출에는 단가가 없다`() {
        assertTrue(repository.findEffective("openai", "gpt-image-2", Instant.parse("2026-10-17T23:59:59Z")).isEmpty())
    }

    /** 앞으로 더해질 마이그레이션의 표도 모두 본다. 읽기에 성공하면 [PriceRates] 의 불변식을 통과한 것이다. */
    @Test
    fun `마이그레이션이 넣은 표는 모두 빈 표도 음수 단가도 없이 읽힌다`() {
        val books = em.createQuery("select b from ModelPriceBook b", ModelPriceBook::class.java).resultList

        assertTrue(books.isNotEmpty(), "마이그레이션이 단가표를 넣지 않았다")
        for (book in books) {
            assertTrue(book.rates.usdPerMillion.isNotEmpty(), "${book.model} 의 표에 단가가 없다")
            for ((field, usdPerMillion) in book.rates.usdPerMillion) {
                assertTrue(usdPerMillion.signum() >= 0, "${book.model} 의 $field 단가가 음수다: $usdPerMillion")
            }
        }
    }
}
