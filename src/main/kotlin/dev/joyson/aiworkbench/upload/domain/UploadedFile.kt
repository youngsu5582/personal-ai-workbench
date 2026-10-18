package dev.joyson.aiworkbench.upload.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
 * 사용자가 보관소에 직접 올리기로 한 파일 한 개.
 *
 * **주소를 발급할 때** 만들어진다. 앱은 업로드를 보지 못해서, 올라왔는지와 정말 선언한 형식인지는 [status] 가 나중에 말한다.
 */
@Entity
@Table(
    name = "uploaded_files",
    uniqueConstraints = [UniqueConstraint(name = "uk_uploaded_files_owner_sha256", columnNames = ["owner_user_uuid", "sha256"])],
)
class UploadedFile(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** 외부로 노출하는 식별자. */
    @Column(nullable = false, unique = true, updatable = false)
    val uuid: UUID = UUID.randomUUID(),

    /** 다른 모듈의 행을 가리키므로 FK 없이 uuid 로 둔다 — 근거는 `V2__owner_user_uuid.sql`. */
    @Column(name = "owner_user_uuid", nullable = false, updatable = false)
    val ownerUserUuid: UUID,

    /** 옛 행은 내용을 읽지 않고 해시를 채울 수 없어 null 로 남긴다. 신규 발급에는 SHA-256 이 필수다. */
    @Column(length = 64, updatable = false)
    val sha256: String? = null,

    /** 보관소에서의 위치. **이 모듈 밖으로 내보내지 않는다** — 바이트나 읽기 주소가 필요한 쪽은 이 모듈에 부탁한다. */
    @Column(name = "storage_key", nullable = false, unique = true, updatable = false)
    val storageKey: String,

    /** 이 파일이 **무엇인가**. [status] 가 PENDING 이면 발급 때 선언한 값이고, READY 면 확인된 사실이다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    val metadata: UploadedFileMetadata,

    /**
     * 입력으로 쓸 수 있는가.
     *
     * 엔티티를 고쳐 저장하는 방식으로 바꾸지 않는다. 같은 파일을 여러 요청이 동시에 처음 참조할 수 있어서,
     * 읽고-바꾸고-저장하면 나중 것이 앞 것을 덮는다. 전이는 조건부 UPDATE 로만 한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: UploadStatus = UploadStatus.PENDING,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /** 마지막으로 상태가 바뀐 때. [status] 와 같은 이유로 조건부 UPDATE 가 함께 고친다. */
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = createdAt,
)

/**
 * 앱은 업로드를 보지 못하므로, 올라온 시점이 아니라 **처음 쓰일 때** 검증한다.
 *
 * ```
 * 발급 ─▶ PENDING ─(검증)─┬─▶ READY
 *                        └─▶ REJECTED
 * ```
 *
 * 검증은 `DefaultUploadedFiles.verify` 한 곳에서만 한다. 부르는 때는 둘이다 —
 * 입력으로 쓰려 할 때(`UploadedFiles.findUsable`)와 같은 파일을 다시 발급할 때(`UploadService.issue`).
 * 아무도 부르지 않으면 PENDING 으로 남고, 객체가 아직 없으면 검증하지 않는다.
 */
enum class UploadStatus {
    /** 주소를 발급했다. 올라왔는지, 선언한 형식이 맞는지 아직 모른다. */
    PENDING,

    /** 올라온 바이트가 선언한 형식이다. 입력으로 쓸 수 있다. */
    READY,

    /** 올라온 바이트가 선언 조건과 다르다. 객체는 지우고, 같은 내용의 발급 요청은 다시 열지 않고 거절한다. */
    REJECTED,
    ;

    /** 다시 검증하지 않는 상태. READY 와 REJECTED 에서는 나가는 전이가 없다. */
    val isFinal: Boolean get() = this != PENDING
}
