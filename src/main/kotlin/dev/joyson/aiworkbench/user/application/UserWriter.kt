package dev.joyson.aiworkbench.user.application

import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserView
import dev.joyson.aiworkbench.user.domain.User
import dev.joyson.aiworkbench.user.domain.UserIdentity
import dev.joyson.aiworkbench.user.infrastructure.UserIdentityRepository
import dev.joyson.aiworkbench.user.infrastructure.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 실제 쓰기를 수행하는 트랜잭션 경계이며, 로그인 수단(identity)에 관한 규칙의 주인이다.
 *
 * 메서드 하나가 트랜잭션 하나다. [DefaultUserRegistry] 는 트랜잭션을 열지 않으므로
 * 최초 로그인 경쟁으로 unique 제약을 밟아도 그 트랜잭션만 롤백되고,
 * 이어지는 재조회는 깨끗한 새 트랜잭션에서 수행된다.
 *
 * 그래서 [DefaultUserRegistry.resolveOrRegister] 는 바깥 트랜잭션 안에서 호출하면 안 된다.
 * 그러면 제약 위반이 바깥 트랜잭션을 rollback-only 로 마킹해 재시도가 무의미해진다.
 */
@Service
class UserWriter(
    private val userRepository: UserRepository,
    private val userIdentityRepository: UserIdentityRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    
    @Transactional
    fun loginExisting(command: RegisterIdentityCommand): UserView? {
        val identity = userIdentityRepository
            .findWithUserByIssuerAndSubject(command.issuer, command.subject)
            ?: return null

        identity.recordLogin(command.email)
        // displayName 은 최초 등록 때 정해지고 로그인으로는 갱신하지 않는다.
        // provider 이름으로 매번 덮어쓰면 (1) 사용자가 직접 바꾼 이름이 다음 로그인에 지워지고,
        // (2) 여러 provider 를 연결한 뒤에는 마지막에 로그인한 쪽 이름으로 계속 뒤집힌다.
        // 바꿀 수단은 User.rename() 으로 열어둔다.
        return identity.user.toView()
    }

    @Transactional
    fun register(command: RegisterIdentityCommand): UserView {
        val user = userRepository.save(User(displayName = command.displayName))
        // UNIQUE(issuer, subject) 위반은 attach 안에서 난다. 같은 트랜잭션이라 위의 User 삽입도 함께 롤백된다.
        val identity = attach(user, command)
        log.info("새 사용자를 등록: uuid={} issuer={}", user.uuid, identity.issuer)
        return user.toView()
    }

    @Transactional
    fun link(userId: Long, command: RegisterIdentityCommand): UserView {
        val user = requireUser(userId)
        require(userIdentityRepository.findByUserIdAndIssuer(userId, command.issuer) == null) {
            "이미 연결된 provider: ${command.issuer}"
        }
        val identity = attach(user, command)
        log.info("외부 인증 주체를 통해 연결: uuid={} issuer={}", user.uuid, identity.issuer)
        return user.toView()
    }

    /**
     * 로그인 수단 하나를 해제한다.
     *
     * 마지막 하나는 해제할 수 없다. 해제하면 아무도 이 계정에 들어올 수 없기 때문이다.
     * "자식 행이 최소 1개" 를 강제하는 DB 제약은 존재하지 않으므로 애플리케이션이 막아야 한다.
     *
     * 주의: 아래 count 확인은 TOCTOU 에 열려 있다. 동시에 두 건이 들어오면 둘 다 통과할 수 있다.
     * 해제 UI 가 실제로 생기는 시점에 User 에 낙관적 락(@Version)을 붙이거나
     * 부모 행을 비관적 락으로 잡아야 한다. (컬렉션 매핑이 있었어도 이 문제는 동일했다.)
     */
    @Transactional
    fun unlink(userId: Long, issuer: String): UserView {
        val user = requireUser(userId)
        val normalized = RegisterIdentityCommand.normalizeIssuer(issuer)

        require(userIdentityRepository.countByUserId(userId) > 1) { "마지막 로그인 수단은 해제할 수 없다" }
        val target = requireNotNull(userIdentityRepository.findByUserIdAndIssuer(userId, normalized)) {
            "연결되지 않은 provider 다: $issuer"
        }

        userIdentityRepository.delete(target)
        userIdentityRepository.flush()
        log.info("외부 인증 주체를 해제했다: uuid={} issuer={}", user.uuid, normalized)
        return user.toView()
    }

    private fun attach(user: User, command: RegisterIdentityCommand): UserIdentity {
        val identity = UserIdentity(
            user = user,
            issuer = command.issuer,
            subject = command.subject,
            email = command.email,
        )
        identity.recordLogin(command.email)
        return userIdentityRepository.save(identity)
    }

    private fun requireUser(userId: Long): User =
        userRepository.findById(userId).orElseThrow { IllegalArgumentException("없는 사용자다: $userId") }

}
