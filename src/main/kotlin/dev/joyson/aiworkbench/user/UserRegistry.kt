package dev.joyson.aiworkbench.user

import java.util.UUID

/**
 * user 모듈의 공개 경계다.
 *
 * 다른 모듈은 `User`/`UserIdentity` Entity 와 Repository 를 직접 보지 않고 이 인터페이스만 쓴다.
 *
 * 여기에는 **실제 소비자가 있는 것만** 둔다.
 * 연결/해제(link·unlink)는 구현과 테스트는 있지만 아직 호출하는 흐름이 없어서 내려뒀다.
 * 계정 연결 흐름을 만들 때 그 흐름이 실제로 필요한 모양으로 올린다.
 * 그래서 나중에 UserIdentity 의 저장 구조가 바뀌어도 호출부는 영향을 받지 않는다.
 */
interface UserRegistry {

    /**
     * (issuer, subject) 로 기존 사용자를 찾고, 없으면 새로 만든다(JIT provisioning).
     *
     * 주의: 이메일이 같다는 이유로 기존 User 에 자동 연결하지 않는다. 그건 계정 탈취 경로다.
     * 기존 계정에 붙이는 것은 로그인된 상태에서만 가능한 [linkIdentity] 의 일이다.
     */
    fun resolveOrRegister(command: RegisterIdentityCommand): UserView



    fun findByUuid(uuid: UUID): UserView?

}

/**
 * 외부 인증 주체 하나를 서술한다.
 *
 * data class 가 아닌 이유: `copy()` 가 private 생성자를 우회해
 * 정규화를 건너뛴 인스턴스를 만들 수 있기 때문이다. 정규화가 이 타입의 존재 이유라 그걸 막는다.
 *
 * issuer 는 반드시 [of] 를 거쳐 정규화된다. Google 의 ID token 은 `iss` 를
 * `https://accounts.google.com` 과 `accounts.google.com` 두 형태로 줄 수 있는데,
 * 정규화하지 않으면 같은 사람이 서로 다른 두 행으로 갈라진다.
 */
class RegisterIdentityCommand private constructor(
    val issuer: String,
    val subject: String,
    val displayName: String,
    val email: String?,
) {
    /**
     * email 은 일부러 뺀다.
     *
     * toString 은 예외 메시지·로그에 실려 나가기 쉬운데, 이 커맨드는 로그인 경로를 지나므로
     * 그때마다 개인정보가 따라 나갈 이유가 없다. 필요하면 필드를 직접 찍는다.
     */
    override fun toString(): String =
        "RegisterIdentityCommand(issuer=$issuer, subject=$subject, displayName=$displayName)"

    companion object {
        fun of(issuer: String, subject: String, displayName: String, email: String?): RegisterIdentityCommand {
            require(subject.isNotBlank()) { "subject 는 비어 있을 수 없다" }
            return RegisterIdentityCommand(
                issuer = normalizeIssuer(issuer),
                subject = subject.trim(),
                displayName = displayName.ifBlank { subject },
                email = email?.trim()?.lowercase(),
            )
        }

        fun normalizeIssuer(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            require(trimmed.isNotBlank()) { "issuer 는 비어 있을 수 없다" }
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed.lowercase()
            } else {
                "https://${trimmed.lowercase()}"
            }
        }
    }
}

data class UserView(
    val id: Long,
    val uuid: UUID,
    val displayName: String,
    val active: Boolean,
)

