package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.provider.AppliedParameters
import dev.joyson.aiworkbench.provider.CostUnit
import dev.joyson.aiworkbench.provider.FailureKind as ProviderFailureKind
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ProviderUsage
import dev.joyson.aiworkbench.usage.FailureKind
import dev.joyson.aiworkbench.usage.ImageRequest
import dev.joyson.aiworkbench.usage.ProviderRequest
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
        applied: AppliedParameters?,
    ): RecordProviderCallCommand = base(job, task, providerName, request, latencyMs, calledAt).let { sent ->
        sent.copy(
            succeeded = true,
            // 저쪽이 실제로 쓴 값을 안다면 그쪽이 맞다 — 돈은 요청이 아니라 만들어진 것에 붙는다.
            request = applied?.let { overlay(sent.request, it) } ?: sent.request,
            usageRaw = usage?.raw?.takeIf { it.isNotEmpty() },
            reportedAmount = usage?.reportedCost?.amount,
            reportedUnit = usage?.reportedCost?.unit?.let(::unitOf),
        )
    }

    fun failed(
        job: GenerationJob,
        task: GenerationJobTask,
        providerName: String,
        request: ExternalApiGenerateRequest,
        latencyMs: Int,
        calledAt: Instant,
        failureReason: String,
        failureKind: ProviderFailureKind,
    ): RecordProviderCallCommand = base(job, task, providerName, request, latencyMs, calledAt).copy(
        succeeded = false,
        failureReason = failureReason,
        failureKind = kindOf(failureKind),
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
     * 실패 종류를 우리 이름으로 옮긴다.
     *
     * 두 모듈이 같은 이름의 enum 을 갖고 있어 import 별칭이 필요하다. 이름이 같은 것은
     * 뜻이 같기 때문이고, 그래도 타입을 나눠 둔 것은 **저장되는 모양은 저장하는 모듈이 소유**하기
     * 때문이다. 이 파일이 둘을 잇는 유일한 자리다.
     *
     * `valueOf(name)` 로 줄이지 않는 이유는 [unitOf] 와 같다 — 한쪽에 값이 늘면
     * 런타임에 터지는 대신 컴파일러가 알려주게 한다.
     */
    private fun kindOf(kind: ProviderFailureKind): FailureKind = when (kind) {
        ProviderFailureKind.REJECTED -> FailureKind.REJECTED
        ProviderFailureKind.THROTTLED -> FailureKind.THROTTLED
        ProviderFailureKind.PROVIDER_ERROR -> FailureKind.PROVIDER_ERROR
        ProviderFailureKind.NO_RESPONSE -> FailureKind.NO_RESPONSE
        ProviderFailureKind.UNKNOWN -> FailureKind.UNKNOWN
    }

    /**
     * 저쪽이 답한 실제 값을 보낸 값 위에 덮는다.
     *
     * **통째로 갈아끼우지 않는다.** [AppliedParameters] 의 자리들은 저쪽이 답하지 않으면 null 인데,
     * 그대로 옮기면 우리가 보내서 **알고 있던 값이 모른다는 표시로 바뀐다.** 원장에서 그 둘은
     * 같은 모양이 아니어야 한다 — 하나는 사실이고 하나는 공백이다.
     *
     * 크기는 늘 덮는다. 저쪽이 답하지 않으면 애초에 보낸 값이 거기 들어 있다.
     */
    private fun overlay(sent: ProviderRequest?, applied: AppliedParameters): ProviderRequest = when (sent) {
        is ImageRequest -> ImageRequest(
            width = applied.width,
            height = applied.height,
            quality = applied.quality ?: sent.quality,
            background = applied.background ?: sent.background,
            outputFormat = applied.outputFormat ?: sent.outputFormat,
        )

        // base 가 늘 ImageRequest 를 넣으므로 여기는 닿지 않는다. 종류가 늘면 컴파일러가 알려준다.
        null -> ImageRequest(width = applied.width, height = applied.height, quality = applied.quality)
    }

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
