package dev.joyson.aiworkbench.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Workbench 리소스의 소유권 기준이 되는 사용자다.
 *
 * 로그인 수단은 [UserIdentity] 가 `user_id` 로 이 행을 가리키는 형태로 붙는다.
 * 여기에 컬렉션을 두지 않는 이유:
 * 읽는 곳이 사실상 한 곳뿐인데 모든 조회 경로가 그 컬렉션을 끌고 다니게 되고,
 * 컬렉션이 지켜줄 것처럼 보이는 "로그인 수단 0개 금지" 불변식은 낙관적 락 없이는
 * 어차피 동시성 하에서 강제되지 않는다. 그 규칙은 UserWriter 가 명시적으로 지킨다.
 */
@Entity
@Table(name = "users")
class User(

    /** 내부 DB 식별자. FK·조인은 전부 이 값을 쓴다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** 외부로 노출하는 식별자. JWT 의 sub 에 실린다. */
    @Column(nullable = false, unique = true)
    val uuid: UUID = UUID.randomUUID(),

    @Column(name = "display_name", nullable = false)
    var displayName: String,

    /** 비활성 사용자는 로그인할 수 없고 새 작업의 소유자도 될 수 없다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {

    val active: Boolean
        get() = status == UserStatus.ACTIVE

    fun rename(displayName: String) {
        this.displayName = displayName
    }

    fun disable() {
        status = UserStatus.DISABLED
    }

    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }
}

enum class UserStatus {
    ACTIVE,
    DISABLED,
}
