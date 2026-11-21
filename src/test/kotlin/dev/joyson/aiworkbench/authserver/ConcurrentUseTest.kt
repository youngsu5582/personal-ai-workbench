package dev.joyson.aiworkbench.authserver

import dev.joyson.aiworkbench.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 같은 인가 코드를 거의 동시에 여러 번 보내도 **한 번만** 토큰이 나가는지 본다.
 *
 * 찾기 → 유효 검사 → 저장이 별개 단계라, 저장이 "읽은 시점의 상태일 때만" 이어야 막힌다.
 * 테스트 트랜잭션을 끈다(NOT_SUPPORTED) — 켜 두면 요청 스레드들이 서로의 변경을 보지 못하고 같은 트랜잭션에 묶이지도 않아
 * 실제 상황이 아니다. 대신 만든 행을 직접 지운다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConcurrentUseTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val properties: AuthserverProperties,
    private val jdbc: JdbcTemplate,
) : IntegrationTest() {

    private val userUuid = UUID.randomUUID()
    private val console = ConsoleClient(mockMvc, properties, userUuid)

    @AfterTest
    fun cleanUp() {
        jdbc.update("delete from oauth2_authorization where principal_name = ?", userUuid.toString())
    }

    /** [parallel] 개의 요청을 같은 순간에 출발시키고 응답 상태를 모은다. */
    private fun <T> fireTogether(parallel: Int, request: () -> T): List<T> {
        val pool = Executors.newFixedThreadPool(parallel)
        val ready = CountDownLatch(parallel)
        val go = CountDownLatch(1)
        try {
            val futures = (1..parallel).map {
                pool.submit<T> {
                    ready.countDown()
                    go.await()
                    request()
                }
            }
            ready.await()
            go.countDown()
            return futures.map { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `같은 인가 코드를 동시에 여러 번 보내면 한 번만 성공한다`() {
        repeat(3) {
            val code = console.authorizationCode()

            val statuses = fireTogether(8) { console.exchangeStatus(code) }

            assertEquals(1, statuses.count { it == 200 }, "성공은 정확히 한 번이어야 한다: $statuses")
            assertEquals(7, statuses.count { it == 400 }, "나머지는 invalid_grant(400) 여야 한다: $statuses")
        }
    }
}
