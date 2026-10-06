package dev.joyson.aiworkbench.usage.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** 구현은 infrastructure 의 `JpaProviderCallRepository` 다. */
interface ProviderCallRepository {

    fun save(call: ProviderCall): ProviderCall

    /** 기간 안의 지출을 모델별로 접는다. [from] 은 포함하고 [to] 는 포함하지 않는다. */
    fun summarizeByModel(owner: UUID, from: Instant, to: Instant): List<ModelUsageRow>
}

/** [ProviderCallRepository.summarizeByModel] 의 한 줄. */
interface ModelUsageRow {
    val provider: String
    val model: String
    val calls: Long
    val succeededCalls: Long

    /** 비용을 아는 호출 수. [calls] 보다 작으면 [costUsd] 는 전체가 아니다. */
    val costKnownCalls: Long

    /** 아는 것만 더한 값. 하나도 모르면 null 이다. */
    val costUsd: BigDecimal?
}
