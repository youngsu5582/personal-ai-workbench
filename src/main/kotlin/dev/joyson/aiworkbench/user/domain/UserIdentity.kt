package dev.joyson.aiworkbench.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 한 User 를 증명하는 외부 인증 주체 하나다.
 *
 * 식별 기준은 `provider` 같은 논리 이름이 아니라 (issuer, subject) 쌍이다.
 * - issuer: 발급자. OIDC 면 ID token 의 iss, OIDC 가 아닌 provider(GitHub 등)는 상수로 정한다.
 * - subject: 그 발급자 안에서 불변인 사용자 식별자. GitHub 의 `login` 처럼 바뀌는 값은 쓰지 않는다.
 *
 * email 은 User 의 속성이 아니라 이 identity 가 주장하는 값이므로 여기에 둔다.
 * 로그인 식별에는 절대 쓰지 않는다 — 계정 탈취 경로가 된다.
 */
@Entity
@Table(
    name = "user_identities",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_identities_issuer_subject", columnNames = ["issuer", "subject"]),
    ],
)
class UserIdentity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val issuer: String,

    @Column(nullable = false)
    val subject: String,

    @Column
    var email: String? = null,

    @Column(name = "linked_at", nullable = false, updatable = false)
    val linkedAt: Instant = Instant.now(),

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,
) {
    /**
     * 로그인 시각을 남기고, provider 가 이번에 알려준 이메일로 갱신한다.
     *
     * 저장된 email 은 최초 값의 박제가 아니라 "이 identity 가 주장하는 현재 값" 이다.
     * 그래서 로그인할 때마다 따라간다.
     *
     * null 이면 기존 값을 유지한다. GitHub 처럼 이메일을 비공개로 둔 계정은
     * userinfo 에 이메일을 주지 않는데, 그걸 "이메일이 없어졌다" 로 해석하면
     * 이전에 알던 값이 지워진다.
     *
     * 이 값은 표시용이다. 로그인 허용 여부(allowlist)는 저장된 값이 아니라
     * 매 로그인마다 provider 가 준 값으로 판단한다.
     */
    fun recordLogin(claimedEmail: String?) {
        lastLoginAt = Instant.now()
        if (claimedEmail != null) {
            email = claimedEmail
        }
    }
}
