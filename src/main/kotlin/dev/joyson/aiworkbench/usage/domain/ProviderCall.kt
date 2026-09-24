package dev.joyson.aiworkbench.usage.domain

import dev.joyson.aiworkbench.usage.ProviderRequest

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Provider 호출 한 번. **성공도 실패도 한 행이다.**
 *
 * 지출의 단위는 "사용량" 이 아니라 "호출" 이다 — 실패한 호출도 시간을 쓰고 때로는 돈도 쓴다.
 * 그래서 이름이 Usage 가 아니라 Call 이다.
 *
 * **사실은 불변이고, 해석만 나중에 붙는다.** 호출이 일어났다는 것·토큰이 얼마였다는 것은 안 바뀐다.
 * [cost] 는 그 사실에 대한 우리 해석이라 단가표를 알게 되면 붙고, 표가 고쳐지면 다시 계산된다.
 * 그래서 이 행에서 바뀌는 자리는 [cost] 하나뿐이다.
 *
 * 다른 모듈의 행을 가리킬 때 **대리키가 아니라 uuid** 를 쓰고 FK 도 두지 않는다.
 * 떼어낼 것을 대비한 느슨한 참조라면 상대의 내부 대리키여서는 안 된다.
 */
@Entity
@Table(
    name = "provider_calls",
    indexes = [
        // "이 사람이 이번 달 얼마 썼나" 가 이 표의 주된 질문이다.
        Index(name = "idx_provider_calls_owner_called", columnList = "owner_user_uuid, called_at"),
        Index(name = "idx_provider_calls_task", columnList = "task_uuid"),
    ],
)
class ProviderCall(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, updatable = false)
    val uuid: UUID = UUID.randomUUID(),

    @Column(name = "task_uuid", nullable = false, updatable = false)
    val taskUuid: UUID,

    @Column(name = "job_uuid", nullable = false, updatable = false)
    val jobUuid: UUID,

    /**
     * 집계 축이라 여기 복사한다.
     *
     * Job 을 타고 가면 알 수 있지만, 그러려면 원장이 다른 모듈의 표를 조인해야 한다.
     * 지출 기록은 **조인 없이 답할 수 있어야** 하고, Job 이 지워져도 남아야 한다.
     */
    @Column(name = "owner_user_uuid", nullable = false, updatable = false)
    val ownerUserUuid: UUID,

    @Column(nullable = false, updatable = false)
    val provider: String,

    @Column(nullable = false, updatable = false)
    val model: String,

    /**
     * Provider 에게 보낸 요청. 종류마다 축이 달라 컬럼이 아니라 JSON 이다.
     *
     * 판별자는 JSON 안의 `type` 이고 그 값이 행에 그대로 박힌다 — 리네임하면 옛 행을 못 읽는다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request", updatable = false)
    val request: ProviderRequest? = null,

    @Column(nullable = false, updatable = false)
    val succeeded: Boolean,

    @Column(name = "failure_reason", updatable = false)
    val failureReason: String? = null,

    @Column(name = "latency_ms", nullable = false, updatable = false)
    val latencyMs: Int,

    /**
     * 응답의 사용량 블록 원문.
     *
     * 컬럼으로 쪼개지 않는 이유가 `option` 과 다르다. 거기는 "집계할 요구가 없어서" 지만
     * 여기는 **모양이 Provider 마다 다르고 우리가 정하지 않기 때문**이다.
     * 토큰을 주는 곳, 크레딧을 주는 곳, 아무것도 안 주는 곳이 한 표에 들어온다.
     *
     * 집계는 이 컬럼이 아니라 [cost] 가 받는다 — 그게 Provider 이질성을 가로지르는 유일한 축이다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "usage_raw", updatable = false)
    val usageRaw: Map<String, Any?>? = null,

    /** Provider 가 비용을 직접 답한 경우만. 토큰 수는 여기 오지 않는다 — 그건 [usageRaw] 에 있다. */
    @Embedded
    val reported: ReportedCost? = null,

    /**
     * 달러로 환산한 비용. 아직 계산 전이면 null 이다.
     *
     * 이 행에서 **유일하게 바뀌는 자리**다. 금액과 근거가 늘 함께 정해지도록 덩어리로 둔다.
     */
    @Embedded
    var cost: CalculatedCost? = null,

    /**
     * 호출한 시각.
     *
     * 단가는 시간에 따라 바뀐다. 이 값이 없으면 "그때의 단가" 를 고를 수 없어
     * 소급 계산이 불가능해진다.
     */
    @Column(name = "called_at", nullable = false, updatable = false)
    val calledAt: Instant = Instant.now(),
)
