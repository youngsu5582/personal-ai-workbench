package dev.joyson.aiworkbench.generation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * Provider 와의 상호작용 **1건**. 결과물 1개가 아니다.
 *
 * 지금은 호출 한 번에 한 장을 받으므로 Task 하나가 곧 이미지 한 장이다. 한 번에 여러 장을 주는
 * 방식으로 부르게 되면 Task 1개에 GeneratedFile 이 여러 개가 된다 — 그래서 단위를 결과물이
 * 아니라 호출로 잡는다. 결과물 단위로 잡으면 그것들이 같은 외부 식별자를 공유하며 항상 함께
 * 움직여, 나눠도 아무것도 나뉘지 않는다.
 *
 * 장수만큼 호출을 나누는 이유는 **한 번에 여러 장을 주지 않는 Provider 에서도 돌아가는 유일한
 * 경로**라서다. 한 번에 여러 장 받는 것은 그 위에 얹는 최적화지 대안이 아니다.
 * 나눠 둔 덕에 병렬로 처리할 수도 있다.
 *
 * 개별 재시도도 되지만 기대만큼은 아니다 — 같은 프롬프트로 나간 호출들은 레이트리밋이나
 * 콘텐츠 거절에서 함께 실패한다. 독립적으로 실패하는 것은 일시적 오류 정도다.
 * 재시도는 새 Task 를 만들지 않고 이 행을 PENDING 으로 되돌린다 — 요청과의 대응이 흐트러지지 않는다.
 */
@Entity
@Table(
    name = "generation_job_tasks",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_job_tasks_job_seq", columnNames = ["job_id", "sequence"]),
    ],
)
class GenerationJobTask(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** 외부로 노출하는 식별자. API 응답과 조회에 쓴다. */
    @Column(nullable = false, unique = true, updatable = false)
    val uuid: UUID = UUID.randomUUID(),

    /** 같은 모듈 안이지만 Job 과 Task 는 다른 Aggregate 로 보고 ID 로 참조한다. */
    @Column(name = "job_id", nullable = false, updatable = false)
    val jobId: Long,

    /** Job 안에서의 순번. 0부터 시작한다. */
    @Column(name = "sequence", nullable = false, updatable = false)
    val sequence: Int,

    /** Provider 가 발급한 외부 작업 식별자. 제출 전에는 없다. */
    @Column(name = "provider_task_id")
    var providerTaskId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TaskStatus = TaskStatus.PENDING,

    /** 제출을 시도한 횟수. 재시도 상한 판단에 쓴다. */
    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    @Column(name = "failure_reason")
    var failureReason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {

    /**
     * Task 는 각자 자기 행만 수정하므로 경쟁하지 않는다.
     * 여기서 엔티티 수정을 쓰는 것은 안전하다 — Job 과 다른 점이다.
     */
    fun transitionTo(next: TaskStatus, providerTaskId: String? = null, failureReason: String? = null) {
        if (status == next) return
        require(status.canTransitionTo(next)) { "허용되지 않은 상태 전이다: $status → $next" }
        status = next
        when (next) {
            TaskStatus.RUNNING -> {
                attemptCount += 1
                providerTaskId?.let { this.providerTaskId = it }
            }
            TaskStatus.FAILED -> this.failureReason = failureReason
            TaskStatus.PENDING -> this.failureReason = null
            TaskStatus.SUCCEEDED -> Unit
        }
    }

    companion object {
        /**
         * 한 Task 를 몇 번까지 시도하나.
         *
         * 호출당 수십 초에 과금까지 되므로 실패한 작업이 자원을 오래 물고 있으면 안 된다.
         * 일시적 5xx·네트워크 블립은 대개 3회 안에 풀리고, 그 밖의 실패는 대개 결정적이라
         * 더 시도해도 같다.
         */
        const val MAX_ATTEMPTS = 3
    }
}
