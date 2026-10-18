package dev.joyson.aiworkbench.usage

import dev.joyson.aiworkbench.TestcontainersConfiguration
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
