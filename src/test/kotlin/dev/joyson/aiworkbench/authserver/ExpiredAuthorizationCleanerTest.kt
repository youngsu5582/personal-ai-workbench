package dev.joyson.aiworkbench.authserver

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.authserver.authorization.ExpiredAuthorizationCleaner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

/** 모든 토큰이 만료된 인가만 지우고, 아직 쓸 수 있는 것은 남기는지 본다. */
class ExpiredAuthorizationCleanerTest @Autowired constructor(
    private val cleaner: ExpiredAuthorizationCleaner,
    private val jdbc: JdbcTemplate,
) : IntegrationTest() {

    /** [codeExpiresIn] · [refreshExpiresIn] 은 지금부터의 간격이다. 음수면 이미 만료. refresh 가 null 이면 교환 전의 인가다. */
    private fun insert(id: String, codeExpiresIn: String, refreshExpiresIn: String?) {
        jdbc.update(
            """
            insert into oauth2_authorization (
                id, registered_client_id, principal_name, redirect_uri, scopes, code_challenge, code_challenge_method,
                authorization_code_hash, authorization_code_issued_at, authorization_code_expires_at,
                refresh_token_hash, refresh_token_issued_at, refresh_token_expires_at
            ) values (
                ?, 'workbench-console', 'cleaner-test', 'https://workbench.test/cb', '', 'challenge', 'S256',
                ?, now() - interval '3 days', now() + ?::interval,
                ?, now() - interval '3 days', now() + ?::interval
            )
            """.trimIndent(),
            id, "code-$id", codeExpiresIn,
            refreshExpiresIn?.let { "refresh-$id" }, refreshExpiresIn ?: "0 seconds",
        )
        if (refreshExpiresIn == null) {
            jdbc.update("update oauth2_authorization set refresh_token_issued_at = null, refresh_token_expires_at = null where id = ?", id)
        }
    }

    private fun remaining(): Set<String> =
        jdbc.query("select id from oauth2_authorization where principal_name = 'cleaner-test'") { rs, _ -> rs.getString(1) }.toSet()

    @Test
    fun `모든 토큰이 만료된 인가만 지운다`() {
        insert("둘 다 만료", codeExpiresIn = "-2 days", refreshExpiresIn = "-1 day")
        insert("교환 전에 코드만 만료", codeExpiresIn = "-1 day", refreshExpiresIn = null)
        insert("코드는 만료됐지만 리프레시는 살아 있다", codeExpiresIn = "-2 days", refreshExpiresIn = "10 days")
        insert("아직 교환 전이고 코드가 살아 있다", codeExpiresIn = "3 minutes", refreshExpiresIn = null)

        val deleted = cleaner.deleteExpired()

        assertEquals(setOf("코드는 만료됐지만 리프레시는 살아 있다", "아직 교환 전이고 코드가 살아 있다"), remaining())
        assertEquals(true, deleted >= 2, "지운 행 수는 이 테스트의 두 행을 포함해야 한다: $deleted")
    }
}
