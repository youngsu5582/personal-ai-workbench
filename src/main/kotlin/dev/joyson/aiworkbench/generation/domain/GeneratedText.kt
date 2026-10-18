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
 * 만들어진 글 한 편. [GeneratedFile] 의 짝이다.
 *
 * Task 의 칸이 아니라 따로 두는 이유는 Task 가 **호출**이지 결과물이 아니어서다. Task 에 칸을 두면
 * 이미지 Task 에서는 그 칸이 늘 비는데, 그 null 은 "아직 안 끝났다" 와 같은 모양이면서 뜻은 "해당 없음" 이다.
 * 여기 두면 종류마다 자기 결과물 행만 갖는다.
 *
 * 보관소가 아니라 행에 둔다. 짧고, 조회 응답에 바로 실려야 해서 한 번 더 내려받게 할 이유가 없다.
 */
@Entity
@Table(
    name = "generated_texts",
    indexes = [Index(name = "idx_generated_texts_task", columnList = "task_id")],
)
class GeneratedText(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** 같은 모듈 안이지만 Task 와 GeneratedText 는 다른 Aggregate 로 보고 ID 로 참조한다. */
    @Column(name = "task_id", nullable = false, updatable = false)
    val taskId: Long,

    /**
     * 모델이 낸 글 **원문**. 구조화된 답도 파싱하지 않고 문자열 그대로 둔다.
     *
     * jsonb 로 두면 키를 다시 늘어놓아 모델이 낸 순서를 잃는다 — 그 순서는 요청한 스키마의 순서다.
     * 객체로 읽는 일은 내보낼 때 한다.
     */
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    val content: String,

    /** 이 결과물에 대해 저쪽이 추가로 알려준 것. 아무것도 안 알려주면 null 이다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_info", updatable = false)
    val providerInfo: ProviderInfo? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
