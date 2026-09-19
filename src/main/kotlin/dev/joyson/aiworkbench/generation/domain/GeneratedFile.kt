package dev.joyson.aiworkbench.generation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
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
    uniqueConstraints = [
        UniqueConstraint(name = "uk_generated_files_task_seq", columnNames = ["task_id", "sequence"]),
    ],
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

    /** 한 호출이 여러 장을 줄 때의 순번. 0부터 시작한다. */
    @Column(name = "sequence", nullable = false, updatable = false)
    val sequence: Int,

    /**
     * 보관소에서의 위치.
     *
     * Task 와 순번으로부터 결정적으로 만들어진다 — 재시도가 같은 키에 덮어쓰도록.
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
