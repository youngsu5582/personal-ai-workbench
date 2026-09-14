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
 * Provider 와의 상호작용 **1건**.
 *
 * 결과물 1개가 아니다. Provider 가 호출 한 번으로 이미지 4장을 주면 Task 는 1개고 Asset 이 4개다.
 * 결과물 단위로 잡으면, 그 4개가 같은 외부 식별자를 공유하며 항상 함께 움직여
 * 나눠도 아무것도 나뉘지 않는다.
 *
 * 이 단위로 나누는 실익은 **병렬 처리와 개별 재시도**다.
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
}
