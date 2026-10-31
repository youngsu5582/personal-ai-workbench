package dev.joyson.aiworkbench.generation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 판정 한 번의 답들. [GeneratedText] · [GeneratedFile] 의 짝이다 — Task 는 호출이고 결과물은 종류마다 자기 행에 둔다.
 *
 * 답을 질문마다 행으로 펼치지 않는다. 한 호출이 함께 낸 답이라 따로 읽을 일이 없고,
 * 순서(질문의 순서)는 목록이 지킨다.
 */
@Entity
@Table(
    name = "generated_decisions",
    indexes = [Index(name = "idx_generated_decisions_task", columnList = "task_id")],
)
class GeneratedDecision(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** 같은 모듈 안이지만 Task 와 GeneratedDecision 은 다른 Aggregate 로 보고 ID 로 참조한다. */
    @Column(name = "task_id", nullable = false, updatable = false)
    val taskId: Long,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    val answers: List<DecisionAnswer>,

    /** 이 결과물에 대해 저쪽이 추가로 알려준 것. 아무것도 안 알려주면 null 이다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_info", updatable = false)
    val providerInfo: ProviderInfo? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
