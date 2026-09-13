package dev.joyson.aiworkbench.user.application

import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import dev.joyson.aiworkbench.user.UserView
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * [UserRegistry] 의 기본 구현.
 *
 * 트랜잭션은 [UserWriter] 가 갖고, 이 클래스는 최초 로그인 경쟁만 조정한다.
 * 같은 (issuer, subject) 로 동시에 두 요청이 들어오면 한쪽이 unique 제약을 밟는데,
 * 그건 오류가 아니라 "상대가 먼저 만들었다" 는 뜻이므로 다시 읽어서 돌려준다.
 */
@Service
class DefaultUserRegistry(
    private val userWriter: UserWriter,
    private val userReader: UserReader,
) : UserRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun resolveOrRegister(command: RegisterIdentityCommand): UserView {
        log.info("사용자 인증 요청: {}", command)
        userWriter.loginExisting(command)?.let { return it }
        return try {
            userWriter.register(command)
        } catch (e: DataIntegrityViolationException) {
            userReader.findByIdentity(command.issuer, command.subject)
                ?: throw e
        }
    }

    fun linkIdentity(userId: Long, command: RegisterIdentityCommand): UserView {
        val alreadyLinked = userReader.findByIdentity(command.issuer, command.subject)
        if (alreadyLinked != null) {
            require(alreadyLinked.id == userId) { "이 외부 계정은 이미 다른 사용자에게 연결되어 있다" }
            return alreadyLinked
        }
        return userWriter.link(userId, command)
    }

    fun unlinkIdentity(userId: Long, issuer: String): UserView = userWriter.unlink(userId, issuer)

    override fun findByUuid(uuid: UUID): UserView? = userReader.findByUuid(uuid)

}
