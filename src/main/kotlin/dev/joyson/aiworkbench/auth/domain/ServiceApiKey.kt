package dev.joyson.aiworkbench.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 한 서비스에 고정된 API 키의 검증 자료와 관리 이력이다.
 * 원문은 보관하지 않는다. 표시용 힌트는 키를 알아보기 위한 값이며 인증에 쓰지 않는다.
 * 만료·폐기 시각을 보존해 목록에서 키의 상태를 확인할 수 있다.
 */
@Entity
@Table(name = "service_api_keys")
class ServiceApiKey(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true, updatable = false)
    val uuid: UUID = UUID.randomUUID(),
    @Column(name = "workspace_uuid", nullable = false, updatable = false)
    val workspaceUuid: UUID,
    @Column(nullable = false, updatable = false)
    val name: String,
    /** 발급된 원문 전체의 SHA-256. 원문을 다시 꺼낼 수 없다. */
    @Column(name = "key_hash", nullable = false, unique = true, updatable = false, length = 64)
    val keyHash: String,
    /** 고정 접두어와 난수 일부만 포함한 표시용 값이다. 고유성을 보장하지 않는다. */
    @Column(name = "key_hint", nullable = false, updatable = false, length = 16)
    val keyHint: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    /** null 이면 만료 시각을 정하지 않은 키다. */
    @Column(name = "expires_at", updatable = false)
    val expiresAt: Instant? = null,
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
) {
    /** 반복 폐기해도 최초 폐기 시각을 유지한다. */
    fun revoke(now: Instant) {
        if (revokedAt == null) revokedAt = now
    }

    /** 폐기되지 않았고 만료 시각 전이면 인증에 쓸 수 있다. 만료 시각과 같은 순간부터는 쓸 수 없다. */
    fun usableAt(now: Instant): Boolean = revokedAt == null && (expiresAt == null || now.isBefore(expiresAt))
}
