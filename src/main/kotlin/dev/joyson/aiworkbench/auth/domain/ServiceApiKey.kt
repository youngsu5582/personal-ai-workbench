package dev.joyson.aiworkbench.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

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
    @Column(name = "key_hash", nullable = false, unique = true, updatable = false, length = 64)
    val keyHash: String,
    @Column(name = "key_hint", nullable = false, updatable = false, length = 16)
    val keyHint: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "expires_at", updatable = false)
    val expiresAt: Instant? = null,
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
) {
    fun revoke(now: Instant) {
        if (revokedAt == null) revokedAt = now
    }
}
