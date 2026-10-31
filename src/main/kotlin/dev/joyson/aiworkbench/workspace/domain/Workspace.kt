package dev.joyson.aiworkbench.workspace.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 사용자가 관리하는 서비스다. 기본 서비스와 추가 서비스는 같은 모델을 쓴다.
 * 소유자는 다른 모듈의 엔티티를 직접 참조하지 않고 UUID 값으로만 가리킨다.
 */
@Entity
@Table(name = "workspaces")
class Workspace(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(nullable = false, unique = true, updatable = false)
    val uuid: UUID = UUID.randomUUID(),
    @Column(name = "owner_user_uuid", nullable = false, updatable = false)
    val ownerUserUuid: UUID,
    @Column(nullable = false)
    var name: String,
    @Column(name = "is_default", nullable = false, updatable = false)
    val isDefault: Boolean = false,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
