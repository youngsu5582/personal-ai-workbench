package dev.joyson.aiworkbench.authserver.authorization

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 만료된 인가를 지운다.
 *
 * 인가 서버 라이브러리는 만료된 인가를 지우지 않는다. 행은 로그인마다 하나 늘고, 쓴 인가 코드의 해시도 행에 남으므로
 * 두면 쌓이기만 한다. 지우는 기준은 **그 행의 모든 토큰이 만료됐는가** 다 — 인가 코드와 리프레시 토큰 중 늦은 만료 시각이 지났으면
 * 더 이상 교환할 수도 갱신할 수도 없다. 리프레시 토큰은 갱신할 때마다 만료 시각이 다시 시작하므로, 쓰고 있는 인가는 지워지지 않는다.
 *
 * 설정 키는 만들지 않았다. 지워도 되는 것만 지우므로 주기를 바꿔야 할 이유가 아직 없다.
 */
@Component
class ExpiredAuthorizationCleaner(private val jdbc: JdbcOperations) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 시작 직후에는 돌지 않는다. 지운 행 수를 돌려준다. */
    @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
    fun deleteExpired(): Int {
        val deleted = jdbc.update(DELETE_EXPIRED)
        if (deleted > 0) log.info("만료된 인가 {}개를 지웠다", deleted)
        return deleted
    }

    private companion object {
        const val DELETE_EXPIRED = """
            delete from oauth2_authorization
            where greatest(
                coalesce(authorization_code_expires_at, '-infinity'::timestamptz),
                coalesce(refresh_token_expires_at, '-infinity'::timestamptz)
            ) < now()
        """
    }
}
