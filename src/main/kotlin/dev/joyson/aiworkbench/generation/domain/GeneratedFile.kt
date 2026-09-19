package dev.joyson.aiworkbench.generation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Index
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * 만들어진 결과물 한 개.
 *
 * 바이트는 여기 없다. 보관소에 있고 여기는 **어디에 있는지와 무엇인지**만 적는다 —
 * 목록을 보여주거나 용량을 집계할 때 이미지를 읽어올 이유가 없다.
 *
 * Task 와 1:N 이다. Provider 가 호출 한 번으로 여러 장을 주면 Task 는 1개고 GeneratedFile 이 여러 개다.
 */
@Entity
@Table(
    name = "generated_files",
    // 유니크 제약을 걸지 않는다. "같은 Task 가 두 번 성공 처리되지 않는다" 는 Task 의 불변식이라
    // Task 의 상태 전이가 지켜야 한다 — 자식 테이블의 제약으로 대신 지키면 그 제약을 성립시키려고
    // 뜻 없는 컬럼이 따라붙는다. 인덱스는 조회 때문에 둔다.
    indexes = [Index(name = "idx_generated_files_task", columnList = "task_id")],
)
class GeneratedFile(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** 외부로 노출하는 식별자. */
    @Column(nullable = false, unique = true, updatable = false)
    val uuid: UUID = UUID.randomUUID(),

    /** 같은 모듈 안이지만 Task 와 GeneratedFile 은 다른 Aggregate 로 보고 ID 로 참조한다. */
    @Column(name = "task_id", nullable = false, updatable = false)
    val taskId: Long,

    /**
     * 보관소에서의 위치.
     *
     * [uuid] 로부터 만들어진다 — 한 Task 가 파일을 몇 개 내든 겹치지 않는다.
     * 이 값이 없으면 파일은 저장돼 있는데 아무도 못 찾는다.
     */
    @Column(name = "storage_key", nullable = false, updatable = false)
    val storageKey: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    val metadata: FileMetadata,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
