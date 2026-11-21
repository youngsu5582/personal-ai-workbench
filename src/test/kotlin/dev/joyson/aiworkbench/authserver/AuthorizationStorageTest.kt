package dev.joyson.aiworkbench.authserver

import dev.joyson.aiworkbench.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 발급한 인가가 DB 에 어떤 모양으로 남는지 본다.
 *
 * 앱 모듈의 테스트는 인가 서버 라이브러리의 클래스를 컴파일 경로에서 볼 수 없으므로, 저장소를 부르지 않고 SQL 로 테이블을 읽는다.
 */
class AuthorizationStorageTest @Autowired constructor(
    mockMvc: MockMvc,
    properties: AuthserverProperties,
    private val jdbc: JdbcTemplate,
) : IntegrationTest() {

    private val userUuid = UUID.randomUUID()
    private val console = ConsoleClient(mockMvc, properties, userUuid)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    /** 이 사용자의 행 전체를 글로 바꾼 것. 어느 열에든 원문이 남아 있으면 여기 보인다. */
    private fun rowText(): String = checkNotNull(
        jdbc.queryForObject("select t::text from oauth2_authorization t where principal_name = ?", String::class.java, userUuid.toString()),
    )

    @Test
    fun `인가 코드는 원문이 아니라 해시로 남는다`() {
        val code = console.authorizationCode()

        assertEquals(1, jdbc.queryForObject("select count(*) from oauth2_authorization where authorization_code_hash = ?", Int::class.java, sha256(code)))
        assertFalse(rowText().contains(code), "인가 코드 원문이 DB 에 있다")
    }

    @Test
    fun `쓴 인가 코드는 쓸 수 없다고 표시된다`() {
        val code = console.authorizationCode()
        console.exchange(code)

        assertEquals(
            true,
            jdbc.queryForObject("select authorization_code_invalidated from oauth2_authorization where principal_name = ?", Boolean::class.java, userUuid.toString()),
        )
    }

    @Test
    fun `접근 토큰은 DB 에 남지 않는다`() {
        val accessToken = console.login()["access_token"].asString()

        assertFalse(rowText().contains(accessToken), "접근 토큰이 DB 에 있다")
        // 접근 토큰 JWT 의 머리말은 항상 이 글자로 시작한다. 토큰 모양의 값이 어느 열에도 없어야 한다.
        assertFalse(rowText().contains("eyJ"), "JWT 모양의 값이 DB 에 있다")
    }

    @Test
    fun `인가에는 사용자 이름만 남고 Google 프로필은 남지 않는다`() {
        console.authorizationCode()

        val text = rowText()
        assertTrue(text.contains(userUuid.toString()))
        // googleLogin 이 만드는 Google subject · 표시 이름 · ID 토큰.
        listOf("google-subject-1", "Joyson", "id-token").forEach { assertFalse(text.contains(it), "인가에 $it 가 남아 있다") }
    }
}
