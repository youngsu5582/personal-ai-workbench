package dev.joyson.aiworkbench.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Workbench 리소스의 소유권 기준이 되는 사용자다.
 *
 * 외부 인증 연동 전까지는 `externalSubject`를 로컬 인증 주체 식별자로 사용한다.
 */
@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),

    /** 외부 인증 시스템의 주체 식별자이며, 사용자 간 중복될 수 없다. */
    @Column(name = "external_subject", unique = true)
    var externalSubject: String? = null,

    @Column(name = "display_name", nullable = false)
    var displayName: String,

    /** 비활성 사용자는 새로운 생성 작업의 소유자가 될 수 없다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }
}

enum class UserStatus {
    ACTIVE,
    DISABLED,
}
