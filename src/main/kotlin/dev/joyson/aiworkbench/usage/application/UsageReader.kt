package dev.joyson.aiworkbench.usage.application

import dev.joyson.aiworkbench.usage.infrastructure.ProviderCallRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * 지출을 모델별로 접어 보여준다.
 *
 * **비용을 모르는 호출이 몇 건인지 함께 낸다.** 단가표가 붙기 전에는 모든 호출의 비용이 null 이라
 * 합계만 보여주면 "0 달러를 썼다" 고 읽힌다. 모르는 것을 0 으로 보여주는 것이 제일 나쁘다.
 */
@Service
class UsageReader(
    private val repository: ProviderCallRepository,
) {

    @Transactional(readOnly = true)
    fun summarize(ownerUuid: UUID, from: Instant, to: Instant): UsageSummaryView {
        val rows = repository.summarizeByModel(ownerUuid, from, to).map {
            ModelUsageView(
                provider = it.provider,
                model = it.model,
                calls = it.calls,
                succeededCalls = it.succeededCalls,
                failedCalls = it.calls - it.succeededCalls,
                costKnownCalls = it.costKnownCalls,
                costUsd = it.costUsd,
            )
        }

        return UsageSummaryView(
            from = from,
            to = to,
            calls = rows.sumOf { it.calls },
            succeededCalls = rows.sumOf { it.succeededCalls },
            costKnownCalls = rows.sumOf { it.costKnownCalls },
            // 하나도 모르면 0 이 아니라 null 이다. 0 은 "안 썼다" 는 뜻이고 그건 거짓이다.
            costUsd = rows.mapNotNull { it.costUsd }.takeIf { it.isNotEmpty() }?.reduce(BigDecimal::add),
            byModel = rows,
        )
    }
}

data class UsageSummaryView(
    val from: Instant,
    val to: Instant,
    val calls: Long,
    val succeededCalls: Long,
    val costKnownCalls: Long,
    val costUsd: BigDecimal?,
    val byModel: List<ModelUsageView>,
) {
    /** 합계가 기간 전체를 말하는가. false 면 [costUsd] 는 아는 것만 더한 값이다. */
    val costComplete: Boolean get() = calls == costKnownCalls
}

data class ModelUsageView(
    val provider: String,
    val model: String,
    val calls: Long,
    val succeededCalls: Long,
    val failedCalls: Long,
    val costKnownCalls: Long,
    val costUsd: BigDecimal?,
)
