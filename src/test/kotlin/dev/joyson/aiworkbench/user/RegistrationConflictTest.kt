package dev.joyson.aiworkbench.user

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.user.application.DefaultUserRegistry
import dev.joyson.aiworkbench.user.application.UserReader
import dev.joyson.aiworkbench.user.application.UserWriter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 최초 로그인 경쟁(같은 외부 계정으로 동시에 두 요청)의 처리를 고정한다.
 *
 * 테스트 트랜잭션을 끄는 것(NOT_SUPPORTED)이 핵심이다.
 * 켜둔 채로 하면 UserWriter 가 테스트 트랜잭션에 합류해 프로덕션과 다른 상황을 보게 된다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RegistrationConflictTest @Autowired constructor(
    private val userWriter: UserWriter,
    private val userRegistry: DefaultUserRegistry,
) : IntegrationTest() {
    private fun command(subject: String) =
        RegisterIdentityCommand.of("https://accounts.google.com", subject, "Joyson", "j@example.com")

    /**
     * DefaultUserRegistry 의 재시도는 "중복 등록이 DataIntegrityViolationException 으로 온다" 에 기대고 있다.
     * 그 계약이 깨지면 재시도가 조용히 빗나가므로 여기서 못박는다.
     */
    @Test
    fun `중복 identity 로 등록하면 DataIntegrityViolationException 이 호출자에게 전달된다`() {
        userWriter.register(command("race-1"))

        assertFailsWith<DataIntegrityViolationException> {
            userWriter.register(command("race-1"))
        }
    }

    @Test
    fun `경쟁에서 진 쪽도 같은 사용자를 돌려받는다`() {
        val first = userRegistry.resolveOrRegister(command("race-2"))

        val second = userRegistry.resolveOrRegister(command("race-2"))

        assertEquals(first.id, second.id)
    }
}
