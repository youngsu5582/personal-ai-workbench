package dev.joyson.aiworkbench

import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 모든 테스트가 **진짜 PostgreSQL** 을 쓰게 한다.
 *
 * H2 를 쓰면 `jsonb`·`uuid`·`timestamp with time zone`·체크 제약이 흉내로만 검증된다.
 * 실제로 물린 적도 있다 — 같은 이름 파라미터를 두 번 쓴 네이티브 쿼리가 0행을 돌려주던 문제는
 * PostgreSQL 에서만 드러났다.
 *
 * 테스트 클래스마다 컨테이너를 띄우지 않으려고 JUnit 세션이 열릴 때 한 번만 띄우고,
 * 접속 정보를 시스템 프로퍼티로 넘긴다. 시스템 프로퍼티는 `application-test.yaml` 보다
 * 우선하므로 테스트 코드를 한 줄도 고치지 않아도 된다.
 *
 * 컨테이너는 JVM 이 끝날 때 Testcontainers 의 ryuk 이 치운다.
 */
class PostgresTestDatabase : LauncherSessionListener {

    override fun launcherSessionOpened(session: LauncherSession) {
        if (started) return
        started = true

        val container = PostgreSQLContainer(DockerImageName.parse(IMAGE))
        container.start()

        System.setProperty("spring.datasource.url", container.jdbcUrl)
        System.setProperty("spring.datasource.username", container.username)
        System.setProperty("spring.datasource.password", container.password)
    }

    private companion object {
        /**
         * 운영과 같은 세대로 고정한다. `latest` 는 어느 날 올라가서 어제 통과하던 테스트를 깨뜨린다.
         */
        const val IMAGE = "postgres:16-alpine"

        @Volatile
        var started = false
    }
}
