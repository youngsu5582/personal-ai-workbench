package dev.joyson.aiworkbench.authserver

import dev.joyson.aiworkbench.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 같은 리프레시 토큰으로 동시에 갱신해도 **한 쪽만** 이기고, 이긴 쪽의 새 토큰이 저장되는지 본다.
 *
 * 막지 못하면 둘 다 새 토큰을 받고 마지막 저장만 남아, 먼저 받은 쪽은 저장되지 않은 토큰을 들고 있게 된다.
 * 탭 두 개가 같은 쿠키로 동시에 갱신하는 정상 상황에서도 생긴다. 이유와 테스트 트랜잭션을 끄는 까닭은 [ConcurrentUseTest] 와 같다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConcurrentRefreshTest @Autowired constructor(
    mockMvc: MockMvc,
    properties: AuthserverProperties,
    private val jdbc: JdbcTemplate,
) : IntegrationTest() {

    private val userUuid = UUID.randomUUID()
    private val console = ConsoleClient(mockMvc, properties, userUuid)
    private val json = JsonMapper.builder().build()

    @AfterTest
    fun cleanUp() {
        jdbc.update("delete from oauth2_authorization where principal_name = ?", userUuid.toString())
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun `같은 리프레시 토큰으로 동시에 갱신하면 한 번만 성공하고 그 토큰이 저장된다`() {
        repeat(3) {
            val refreshToken = console.login()["refresh_token"].asString()
            val parallel = 8
            val pool = Executors.newFixedThreadPool(parallel)
            val ready = CountDownLatch(parallel)
            val go = CountDownLatch(1)
            val responses = try {
                val futures = (1..parallel).map {
                    pool.submit<Pair<Int, String>> {
                        ready.countDown()
                        go.await()
                        val response = console.refresh(refreshToken).andReturn().response
                        response.status to response.contentAsString
                    }
                }
                ready.await()
                go.countDown()
                futures.map { it.get() }
            } finally {
                pool.shutdownNow()
            }

            val statuses = responses.map { it.first }
            assertEquals(1, statuses.count { it == 200 }, "성공은 정확히 한 번이어야 한다: $statuses")
            assertEquals(parallel - 1, statuses.count { it == 400 }, "나머지는 invalid_grant(400) 여야 한다: $statuses")

            val winner = json.readTree(responses.single { it.first == 200 }.second)["refresh_token"].asString()
            val stored = jdbc.queryForObject(
                "select refresh_token_hash from oauth2_authorization where principal_name = ? order by authorization_code_issued_at desc limit 1",
                String::class.java,
                userUuid.toString(),
            )
            assertEquals(sha256(winner), stored, "이긴 요청이 받은 리프레시 토큰이 저장돼 있어야 한다")
        }
    }
}
