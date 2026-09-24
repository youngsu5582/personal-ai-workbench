package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.provider.CostUnit
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ProviderUsage
import dev.joyson.aiworkbench.usage.ImageRequest
import dev.joyson.aiworkbench.usage.RecordProviderCallCommand
import dev.joyson.aiworkbench.usage.ReportedUnit
import java.time.Instant

/**
 * 방금 일어난 Provider 호출을 usage 모듈의 어휘로 옮긴다.
 *
 * [ProviderRequestFactory] 의 대칭이다. 그쪽이 나갈 때 번역하고 이쪽이 들어올 때 번역한다.
 * `provider` 도 `usage` 도 서로를 모르므로, 둘을 잇는 책임은 양쪽에 의존하는 여기에 있다.
 *
 * 성공과 실패를 **다른 함수로** 가른다. 한 함수에 `failureReason: String?` 을 두면
 * "사유가 없으면 성공" 이라는 약속을 사람이 기억해야 하는데, 그 약속은 호출 지점에서 조용히 깨진다.
 */
object ProviderCallFactory {

    fun succeeded(
        job: GenerationJob,
        task: GenerationJobTask,
        providerName: String,
        request: ExternalApiGenerateRequest,
        latencyMs: Int,
        calledAt: Instant,
        usage: ProviderUsage?,
    ): RecordProviderCallCommand = base(job, task, providerName, request, latencyMs, calledAt).copy(
        succeeded = true,
        usageRaw = usage?.raw?.takeIf { it.isNotEmpty() },
        reportedAmount = usage?.reportedCost?.amount,
        reportedUnit = usage?.reportedCost?.unit?.let(::unitOf),
    )

    fun failed(
        job: GenerationJob,
        task: GenerationJobTask,
        providerName: String,
        request: ExternalApiGenerateRequest,
        latencyMs: Int,
        calledAt: Instant,
        failureReason: String,
    ): RecordProviderCallCommand = base(job, task, providerName, request, latencyMs, calledAt).copy(
        succeeded = false,
        failureReason = failureReason,
    )

    private fun base(
        job: GenerationJob,
        task: GenerationJobTask,
        providerName: String,
        request: ExternalApiGenerateRequest,
        latencyMs: Int,
        calledAt: Instant,
    ) = RecordProviderCallCommand(
        taskUuid = task.uuid,
        jobUuid = job.uuid,
        ownerUserUuid = job.ownerUserUuid,
        provider = providerName,
        model = job.model,
        // 요청에 적힌 비율이 아니라 Provider 에게 실제로 보낸 픽셀이다. 과금이 걸리는 축은 이쪽이다.
        // 이미지 종류를 아는 것은 여기다 — usage 는 어떤 종류가 왔는지 판별자로만 안다.
        request = ImageRequest(
            width = request.width,
            height = request.height,
            quality = request.quality.name.lowercase(),
        ),
        succeeded = false,
        latencyMs = latencyMs,
        calledAt = calledAt,
    )

    /**
     * 단위를 우리 이름으로 옮긴다.
     *
     * `valueOf(name)` 로 줄이지 않는 이유는 [ProviderRequestFactory.qualityOf] 와 같다 —
     * 한쪽에 값이 늘면 런타임에 터지는 대신 컴파일러가 알려주게 한다.
     */
    private fun unitOf(unit: CostUnit): ReportedUnit = when (unit) {
        CostUnit.USD -> ReportedUnit.USD
        CostUnit.PROVIDER_CREDIT -> ReportedUnit.PROVIDER_CREDIT
    }
}
