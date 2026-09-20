package dev.joyson.aiworkbench.usage

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * usage 모듈의 공개 경계다 — 바깥으로 나간 돈을 적는 곳.
 *
 * 이 모듈은 `generation` 도 `provider` 도 `user` 도 **모른다.** 커맨드가 원시 타입과 [UUID] 로만
 * 이루어진 것이 그 규칙을 코드로 강제한다. 여기에 다른 모듈의 타입이 하나라도 섞이면
 * 경계가 무너지고, 그 사실은 이 파일이 아니라 부르는 쪽에서 컴파일 에러로 드러난다.
 */
interface ProviderCallRecorder {

    /**
     * Provider 호출 한 번을 적는다. **성공·실패를 가리지 않는다** —
     * 실패한 호출도 시간을 쓰고, 때로는 돈도 쓴다(타임아웃이 대표적이다).
     *
     * 이 메서드는 **던지지 않는 것을 목표로 한다.** 부르는 쪽은 이미 돈을 쓴 뒤라
     * 여기서 예외가 나 작업이 되돌려지면 재시도가 돈을 또 쓴다.
     */
    fun record(command: RecordProviderCallCommand): UUID
}

/**
 * Provider 호출 한 번에 대해 우리가 아는 전부.
 *
 * [usageRaw] 를 우리 타입으로 접지 않는 이유는 접는 순간 무엇을 버렸는지 알 수 없어지기 때문이다.
 * 나머지 필드는 Provider 가 아니라 **우리가 관측한 사실**이라 우리 어휘로 적는다.
 */
data class RecordProviderCallCommand(
    val taskUuid: UUID,
    val jobUuid: UUID,
    val ownerUserUuid: UUID,

    val provider: String,
    val model: String,

    /** Provider 에게 **실제로 보낸** 것. 종류마다 축이 달라 [ProviderRequest] 가 그것을 가른다. */
    val request: ProviderRequest? = null,

    val succeeded: Boolean,
    val failureReason: String? = null,

    /** Provider 호출만의 시간. 보관 지연이 섞이면 Provider 성능 비교가 안 된다. */
    val latencyMs: Int,

    /** 응답의 사용량 블록 원문. 외부 필드명이 그대로 들어 있다. */
    val usageRaw: Map<String, Any?>? = null,

    /** Provider 가 비용을 **직접 답한** 경우만. 사용량(토큰)은 여기 오지 않는다. */
    val reportedAmount: BigDecimal? = null,
    val reportedUnit: ReportedUnit? = null,

    val calledAt: Instant = Instant.now(),
)

/**
 * Provider 가 답한 비용의 단위.
 *
 * `provider` 모듈에도 같은 모양의 enum 이 있지만 그것을 쓰지 않는다 —
 * 저장되는 모양은 저장하는 모듈이 소유한다. 그쪽 이름을 박으면 그쪽을 조정할 때 기존 행을 못 읽는다.
 * (`ImageMetadata` 와 `FileMetadata` 를 갈라 둔 것과 같은 판단이다.)
 */
enum class ReportedUnit {
    USD,
    PROVIDER_CREDIT,
}
