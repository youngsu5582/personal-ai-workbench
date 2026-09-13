package dev.joyson.aiworkbench.user

import dev.joyson.aiworkbench.user.application.DefaultUserRegistry
import dev.joyson.aiworkbench.user.application.UserReader
import dev.joyson.aiworkbench.user.application.UserWriter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val GOOGLE = "https://accounts.google.com"
private const val GITHUB = "https://github.com"

@ActiveProfiles("test")
@DataJpaTest
@Import(UserWriter::class, UserReader::class, DefaultUserRegistry::class)
class UserRegistryTest @Autowired constructor(
    // 공개 인터페이스가 아니라 구현을 주입한다.
    // link/unlink 는 아직 공개 경계에 없고 구현에만 있기 때문이다.
    private val userRegistry: DefaultUserRegistry,
    private val userReader: UserReader,
) {

    private fun command(issuer: String, subject: String, name: String = "Joyson", email: String? = "joyson@example.com") =
        RegisterIdentityCommand.of(issuer, subject, name, email)

    @Test
    fun `최초 로그인이면 사용자를 새로 만든다`() {
        val user = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1"))

        assertEquals("Joyson", user.displayName)
        assertTrue(user.active)
        assertEquals(1, userReader.findIdentities(user.id).size)
        assertEquals(GOOGLE, userReader.findIdentities(user.id).single().issuer)
    }

    @Test
    fun `같은 외부 주체로 다시 로그인하면 같은 사용자다`() {
        val first = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1"))
        val second = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1", name = "Joyson Lee"))

        assertEquals(first.id, second.id)
        assertEquals(first.uuid, second.uuid)
        assertEquals(1, userReader.findIdentities(second.id).size)
        // provider 가 다른 이름을 줘도 표시 이름은 그대로다.
        // 매번 덮어쓰면 여러 provider 를 연결했을 때 마지막 로그인 쪽으로 계속 뒤집힌다.
        assertEquals("Joyson", second.displayName)
    }

    @Test
    fun `issuer 표기가 달라도 같은 사용자로 해석한다`() {
        val canonical = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1"))
        // Google 은 iss 를 스킴 없이 줄 수 있다. 정규화하지 않으면 여기서 사용자가 둘로 갈라진다.
        val shorthand = userRegistry.resolveOrRegister(command("accounts.google.com", "sub-1"))

        assertEquals(canonical.id, shorthand.id)
    }

    @Test
    fun `이메일이 같아도 다른 provider 면 별도 사용자다`() {
        val google = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1"))
        val github = userRegistry.resolveOrRegister(command(GITHUB, "9876"))

        // 이메일 일치로 자동 연결하면 계정 탈취 경로가 된다. 반드시 갈라져야 한다.
        assertNotEquals(google.id, github.id)
    }

    @Test
    fun `로그인된 사용자에게 다른 provider 를 연결할 수 있다`() {
        val user = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1"))

        val linked = userRegistry.linkIdentity(user.id, command(GITHUB, "9876"))

        assertEquals(user.id, linked.id)
        assertEquals(setOf(GOOGLE, GITHUB), userReader.findIdentities(linked.id).map { it.issuer }.toSet())
    }

    @Test
    fun `연결된 GitHub 으로 로그인해도 같은 사용자다`() {
        val user = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1"))
        userRegistry.linkIdentity(user.id, command(GITHUB, "9876"))

        val viaGithub = userRegistry.resolveOrRegister(command(GITHUB, "9876"))

        assertEquals(user.id, viaGithub.id)
    }

    /** 이 규칙은 User 엔티티에 있다가 UserWriter 로 내려왔다. 자리를 옮겨도 유지되는지 고정한다. */
    @Test
    fun `한 사용자에게 같은 provider 를 두 번 연결할 수 없다`() {
        val user = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1"))

        // 같은 issuer, 다른 subject — 구글 계정 두 개를 한 User 에 붙이려는 시도다.
        assertFailsWith<IllegalArgumentException> {
            userRegistry.linkIdentity(user.id, command(GOOGLE, "sub-2"))
        }
    }

    @Test
    fun `연결되지 않은 provider 는 해제할 수 없다`() {
        val user = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1"))
        userRegistry.linkIdentity(user.id, command(GITHUB, "9876"))

        assertFailsWith<IllegalArgumentException> {
            userRegistry.unlinkIdentity(user.id, "https://gitlab.com")
        }
    }

    @Test
    fun `다른 사용자에게 이미 연결된 외부 계정은 연결할 수 없다`() {
        val owner = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1"))
        val other = userRegistry.resolveOrRegister(command(GITHUB, "9876"))

        assertFailsWith<IllegalArgumentException> {
            userRegistry.linkIdentity(owner.id, command(GITHUB, "9876"))
        }
    }

    @Test
    fun `마지막 로그인 수단은 해제할 수 없다`() {
        val user = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1"))

        assertFailsWith<IllegalArgumentException> {
            userRegistry.unlinkIdentity(user.id, GOOGLE)
        }
    }

    @Test
    fun `두 개 이상일 때는 해제할 수 있다`() {
        val user = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1"))
        userRegistry.linkIdentity(user.id, command(GITHUB, "9876"))

        val remaining = userRegistry.unlinkIdentity(user.id, GITHUB)

        assertEquals(listOf(GOOGLE), userReader.findIdentities(remaining.id).map { it.issuer })
    }

    /**
     * 참조 무결성은 애플리케이션이 먼저 막는다.
     * 호출자에게 도달하는 것은 제약 이름이 섞인 DB 예외가 아니라 이유가 적힌 도메인 예외여야 한다.
     * (DB 의 FK 는 이 검사가 놓친 경로를 위한 최후 방어로 남는다.)
     */
    @Test
    fun `없는 사용자에게 연결하면 DB 예외가 아니라 도메인 예외가 난다`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            userRegistry.linkIdentity(999_999L, command(GOOGLE, "sub-x"))
        }

        assertTrue(ex.message!!.contains("없는 사용자다"), "실제 메시지: ${ex.message}")
    }

    @Test
    fun `다시 로그인하면 provider 가 준 이메일로 갱신된다`() {
        userRegistry.resolveOrRegister(command(GOOGLE, "sub-1", email = "old@example.com"))

        val updated = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1", email = "new@example.com"))

        assertEquals("new@example.com", userReader.findIdentities(updated.id).single().email)
    }

    /** GitHub 처럼 이메일을 비공개로 둔 계정은 userinfo 에 이메일을 주지 않는다. */
    @Test
    fun `provider 가 이메일을 주지 않으면 기존 값을 지우지 않는다`() {
        userRegistry.resolveOrRegister(command(GOOGLE, "sub-1", email = "keep@example.com"))

        val afterNullEmail = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1", email = null))

        assertEquals("keep@example.com", userReader.findIdentities(afterNullEmail.id).single().email)
    }

    @Test
    fun `uuid 로 조회할 수 있다`() {
        val user = userRegistry.resolveOrRegister(command(GOOGLE, "sub-1"))

        assertEquals(user.id, userRegistry.findByUuid(user.uuid)?.id)
        assertNull(userRegistry.findByUuid(java.util.UUID.randomUUID()))
    }
}
