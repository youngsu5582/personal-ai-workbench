package dev.joyson.aiworkbench.usage.api

import dev.joyson.aiworkbench.ownership.OwnerContext
import dev.joyson.aiworkbench.usage.application.UsageReader
import dev.joyson.aiworkbench.usage.application.UsageSummaryView
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * 내 지출.
 *
 * usage 모듈이 자기 API 를 직접 낸다. generation 을 거치면 생성 흐름이 자기 관심사도 아닌
 * 조회를 들고 있게 되고, 그러려고 usage 가 View 타입을 공개해야 한다.
 *
 * 남의 지출은 볼 수 없다 — 소유자는 요청에서 받지 않고 [OwnerContext] 에서만 나온다.
 */
@RestController
class UsageController(
    private val usageReader: UsageReader,
) {

    /**
     * 기본 구간은 **이번 달**이다. "이번 달 얼마 썼나" 가 이 화면의 주된 질문이라
     * 파라미터 없이 부르는 것이 그 질문이어야 한다.
     */
    @GetMapping("/api/usage")
    fun summary(
        owner: OwnerContext,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: Instant?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: Instant?,
    ): ResponseEntity<UsageSummaryResponse> {
        val end = to ?: Instant.now()
        val start = from ?: startOfMonth(end)
        if (!start.isBefore(end)) {
            // 빈 결과와 구분되어야 한다 — 0 건인 것과 물음이 말이 안 되는 것은 다르다.
            return ResponseEntity.badRequest().build()
        }

        return ResponseEntity.ok(usageReader.summarize(owner.uuid, start, end).toResponse())
    }

    private fun startOfMonth(at: Instant): Instant =
        at.atOffset(ZoneOffset.UTC).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant()
}

/**
 * [costComplete] 를 빼지 않는다.
 *
 * 단가표가 붙기 전에는 [costUsd] 가 null 이거나 일부만 더한 값이다.
 * 이 플래그가 없으면 화면이 그것을 "0 달러 썼다" 로 읽는다.
 */
data class UsageSummaryResponse(
    val from: Instant,
    val to: Instant,
    val calls: Long,
    val succeededCalls: Long,
    val failedCalls: Long,
    val costUsd: String?,
    val costKnownCalls: Long,
    val costComplete: Boolean,
    val byModel: List<ModelUsageResponse>,
)

data class ModelUsageResponse(
    val provider: String,
    val model: String,
    val calls: Long,
    val succeededCalls: Long,
    val failedCalls: Long,
    val costUsd: String?,
    val costKnownCalls: Long,
)

/**
 * 금액을 문자열로 내보낸다.
 *
 * JSON number 로 내면 자바스크립트가 배정도 부동소수로 받는데, 0.00000019 같은 값이 섞이면
 * 더하는 쪽에서 눈에 안 띄게 어긋난다. 저장할 때 반올림하지 않은 이유와 같다.
 */
private fun UsageSummaryView.toResponse() = UsageSummaryResponse(
    from = from,
    to = to,
    calls = calls,
    succeededCalls = succeededCalls,
    failedCalls = calls - succeededCalls,
    costUsd = costUsd?.toPlainString(),
    costKnownCalls = costKnownCalls,
    costComplete = costComplete,
    byModel = byModel.map {
        ModelUsageResponse(
            provider = it.provider,
            model = it.model,
            calls = it.calls,
            succeededCalls = it.succeededCalls,
            failedCalls = it.failedCalls,
            costUsd = it.costUsd?.toPlainString(),
            costKnownCalls = it.costKnownCalls,
        )
    },
)
