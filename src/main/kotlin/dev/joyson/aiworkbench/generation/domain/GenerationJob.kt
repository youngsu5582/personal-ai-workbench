package dev.joyson.aiworkbench.generation.domain

import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * 사용자의 생성 요청 한 건.
 *
 * 요청은 즉시 결과를 돌려주지 않는다. Job 을 만들어 식별자를 반환하고,
 * 실제 처리는 Task 로 갈라져 Worker 가 비동기·병렬로 수행한다.
 *
 * **진행 개수를 컬럼으로 갖지 않는다.** 완료 수는 Task 를 집계해 얻는다.
 * 카운터를 두면 Task 완료가 동시에 일어날 때 read-modify-write 로 값이 유실된다.
 */
@Entity
@Table(name = "generation_jobs")
class GenerationJob(

    /** 내부 DB 식별자. FK·조인은 전부 이 값을 쓴다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** 외부로 노출하는 식별자. API 응답과 조회에 쓴다. */
    @Column(nullable = false, unique = true, updatable = false)
    val uuid: UUID = UUID.randomUUID(),

    /**
     * 소유자. `users.uuid` 를 가리키지만 **FK 도 JPA 연관관계도 두지 않는다.**
     *
     * generation 과 user 는 다른 모듈이고, 모듈 경계를 넘는 스키마 결합은
     * 나중에 모듈을 떼어낼 때 그 이음매를 막는다.
     *
     * `users.id` 가 아니라 uuid 인 이유가 둘이다.
     *
     * 하나는 위와 같은 이유의 연장이다 — 떼어낼 것을 대비한 느슨한 참조라면 상대 모듈의
     * **내부 대리키**여서는 안 된다. 별도 서비스가 되는 순간 그 id 공간은 공유되지 않는다.
     *
     * 다른 하나는 워커다. 보관소 키에 소유자가 들어가는데 **워커는 요청 맥락이 없는 비동기 실행**이라
     * 이 행에서 바로 읽어야 한다. id 만 두면 user 모듈에 물어봐야 하고, 사용자가 사라진 경우
     * 키를 만들지 못해 생성과 무관한 이유로 Task 가 실패한다.
     */
    @Column(name = "owner_user_uuid", nullable = false, updatable = false)
    val ownerUserUuid: UUID,

    /**
     * 생성 입력. 종류마다 모양이 달라 JSON 한 컬럼에 담는다.
     *
     * 종류별 테이블로 나누지 않는 이유: 지금 필요한 것은 "그대로 보관했다가 adapter 에 넘기는" 것뿐이고,
     * 옵션 값으로 검색하거나 집계할 요구가 없다. 그런 요구가 생기면 그때 컬럼으로 승격한다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    val option: GenerationOption,

    /**
     * 무엇으로 만들 것인가. `gpt-image-2` 처럼 Provider 가 아는 모델 이름이다.
     *
     * [option] 안이 아니라 밖에 두는 이유: option 은 "무엇을 만들까" 고 model 은 "무엇으로 만들까" 다.
     * 그리고 JSON 안에 묻으면 "이 모델로 돌린 작업" 을 조회·집계할 수 없다.
     *
     * 어느 Provider 가 이 모델을 다루는지는 여기서 모른다 — 접수 시점에 확인하고 넘어온 값이다.
     */
    @Column(nullable = false, updatable = false)
    val model: String,

    @Column(nullable = false, updatable = false)
    val taskCount: Int,

    /**
     * 수명. Task 결과의 요약이 **아니다**.
     *
     * CLOSED 로의 전이는 여러 Task 가 동시에 끝나며 경쟁하므로
     * 엔티티 수정이 아니라 조건부 UPDATE 로만 한다
     * (`GenerationJobRepository.closeIfAllTasksFinished`).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: JobLifecycle = JobLifecycle.PENDING,

    /** Task 를 만들기도 전에 실패한 경우의 사유. Task 개별 실패는 Task 가 갖는다. */
    @Column(name = "failure_reason")
    var failureReason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {

    init {
        require(taskCount in 1..MAX_TASK_COUNT) {
            "결과물 개수는 1~$MAX_TASK_COUNT 이어야 한다: $taskCount"
        }
    }

    /**
     * Task 를 만들고 처리를 시작한다.
     */
    fun dispatch() {
        require(status == JobLifecycle.PENDING) { "이미 처리가 시작된 Job 이다: $status" }
        status = JobLifecycle.DISPATCHED
    }

    fun isOwnedBy(userUuid: UUID): Boolean = ownerUserUuid == userUuid

    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }

    companion object {
        /**
         * 한 요청이 만들 수 있는 결과물 수의 상한.
         *
         * Provider 비용이 이 값에 정비례하므로 상한이 없으면 요청 한 번이 무제한 과금이 된다.
         * 나중에 사용자별 설정으로 분리할 자리다 — 지금은 전역 상수로 둔다.
         */
        const val MAX_TASK_COUNT = 4
    }
}
