package dev.joyson.aiworkbench.authserver

import com.nimbusds.jwt.SignedJWT
import dev.joyson.aiworkbench.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/** 리프레시 토큰의 발급과 회전. */
class RefreshTokenRotationTest @Autowired constructor(
    mockMvc: MockMvc,
    private val properties: AuthserverProperties,
    private val jdbc: JdbcTemplate,
) : IntegrationTest() {

    private val userUuid = UUID.randomUUID()
    private val console = ConsoleClient(mockMvc, properties, userUuid)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun rowsWithRefreshToken(token: String): Int =
        checkNotNull(jdbc.queryForObject("select count(*) from oauth2_authorization where refresh_token_hash = ?", Int::class.java, sha256(token)))

    @Test
    fun `인가 코드를 토큰으로 바꾸면 리프레시 토큰도 나온다`() {
        assertNotNull(console.login()["refresh_token"]?.asString())
    }

    @Test
    fun `리프레시 토큰으로 새 접근 토큰과 새 리프레시 토큰을 받는다`() {
        val first = console.login()

        val second = console.refreshOk(first["refresh_token"].asString())

        assertNotEquals(first["refresh_token"].asString(), second["refresh_token"].asString())
        assertNotEquals(first["access_token"].asString(), second["access_token"].asString())
    }

    @Test
    fun `갱신해서 받은 접근 토큰도 같은 사용자의 것이다`() {
        val refreshed = console.refreshOk(console.login()["refresh_token"].asString())

        val claims = SignedJWT.parse(refreshed["access_token"].asString()).jwtClaimsSet
        assertEquals(userUuid.toString(), claims.subject)
        assertEquals(listOf(properties.audience), claims.audience)
        assertEquals(properties.accessTokenTtl.seconds, (claims.expirationTime.time - claims.issueTime.time) / 1000)
    }

    @Test
    fun `리프레시 토큰은 설정한 수명으로 발급된다`() {
        console.login()

        val days = jdbc.queryForObject(
            "select extract(epoch from refresh_token_expires_at - refresh_token_issued_at) from oauth2_authorization where principal_name = ?",
            Double::class.java,
            userUuid.toString(),
        )
        assertEquals(properties.refreshTokenTtl, Duration.ofSeconds(checkNotNull(days).toLong()))
    }

    @Test
    fun `리프레시 토큰은 원문이 아니라 해시로 남고, 갱신하면 새 토큰의 해시로 바뀐다`() {
        val first = console.login()["refresh_token"].asString()
        assertEquals(1, rowsWithRefreshToken(first))
        assertFalse(rowText().contains(first), "리프레시 토큰 원문이 DB 에 있다")

        val second = console.refreshOk(first)["refresh_token"].asString()

        assertEquals(0, rowsWithRefreshToken(first))
        assertEquals(1, rowsWithRefreshToken(second))
        assertEquals(1, jdbc.queryForObject("select count(*) from oauth2_authorization where principal_name = ?", Int::class.java, userUuid.toString()), "갱신이 행을 새로 만들었다")
        assertFalse(rowText().contains(second), "리프레시 토큰 원문이 DB 에 있다")
    }

    @Test
    fun `회전한 옛 리프레시 토큰은 더 쓸 수 없다`() {
        val first = console.login()["refresh_token"].asString()
        console.refreshOk(first)

        console.refresh(first).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("invalid_grant") }
        }
    }

    private fun rowText(): String = checkNotNull(
        jdbc.queryForObject("select t::text from oauth2_authorization t where principal_name = ?", String::class.java, userUuid.toString()),
    )
}
