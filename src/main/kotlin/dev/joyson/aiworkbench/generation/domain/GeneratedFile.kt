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

/**
 * 받는 쪽이 저장할 이름.
 *
 * 보관소 키와 **무관하게** 짓는다 — 키는 내용 주소(해시)라 거기엔 뜻이 없다.
 * 공개 식별자인 해시로 지어 요청한 주소와 내려받은 파일명이 같은 내용을 가리키게 한다.
 *
 * 두 경로가 이 이름을 쓴다(직접 스트리밍, 서명 주소의 Content-Disposition).
 * 규칙이 갈리면 같은 파일이 경로에 따라 다른 이름으로 저장된다.
 */
fun downloadFileNameOf(sha256: String, mimeType: String): String =
    "$sha256.${MimeTypes.extensionOf(mimeType)}"

/**
 * 만들어진 결과물 한 개. 바이트 대신 보관소 위치와 메타데이터를 기록한다.
 *
 * Task 와 1:N 이다. 같은 바이트를 가진 행도 각 작업의 결과 이력은 남고 공개 해시는 공유한다.
 */
@Entity
@Table(
    name = "generated_files",
    // 같은 해시의 결과 행을 허용한다. 중복 완료는 Task 의 상태 전이가 막고 인덱스는 조회에 쓴다.
    indexes = [
        Index(name = "idx_generated_files_task", columnList = "task_id"),
        Index(name = "idx_generated_files_sha256", columnList = "sha256"),
    ],
)
class GeneratedFile(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** 같은 바이트를 가진 결과 행도 Task 이력은 각각 남고, 공개 파일 식별자는 같다. */
    @Column(length = 64, nullable = false, updatable = false)
    val sha256: String,

    /** 같은 모듈 안이지만 Task 와 GeneratedFile 은 다른 Aggregate 로 보고 ID 로 참조한다. */
    @Column(name = "task_id", nullable = false, updatable = false)
    val taskId: Long,

    /**
     * 보관소에서의 위치.
     *
     * **내용으로 정해진다** — 바이트의 SHA-256 이 키의 본체다. 그래서 같은 바이트를 가진 다른 행과
     * 이 값을 **공유할 수 있다.** 키 규칙은 [dev.joyson.aiworkbench.storage.BlobKey] 에 있다.
     * 파일 행은 작업 결과 하나를 가리키므로, 같은 blob 을 참조하는 여러 행을 허용한다.
     *
     * 공개 식별자 sha256 은 별도 컬럼으로 인덱싱한다. 키에서 매번 문자열을 잘라 조회하지 않는다.
     *
     * 이 값이 없으면 파일은 저장돼 있는데 아무도 못 찾는다.
     */
    @Column(name = "storage_key", nullable = false, updatable = false)
    val storageKey: String,

    /** 이 파일이 **무엇인가**. 바이트를 보면 알 수 있는 것만 담는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    val metadata: FileMetadata,

    /**
     * 이 결과물에 대해 **저쪽이 추가로 알려준 것**. 아무것도 안 알려주면 null 이다.
     *
     * [metadata] 와 나누는 기준은 바이트에서 나오는가다. 한 칸에 섞으면 영상이 들어올 때
     * metadata 만 바뀌어야 하는데 이 값들까지 같이 흔들린다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_info", updatable = false)
    val providerInfo: ProviderInfo? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
