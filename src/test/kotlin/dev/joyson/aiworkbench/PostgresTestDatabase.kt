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
 * ### 왜 `@ServiceConnection` 자동 설정이 아닌가
 *
 * 그쪽이 Boot 관용구이고 더 깔끔해 보이지만 **테스트 슬라이스에서 빠진다.**
 * `@DataJpaTest` 는 자기 슬라이스 목록에 있는 자동 설정만 켜므로, 우리 것은 적용되지 않는다.
 * 그러면 `spring.datasource.url` 이 비고, `application.yaml` 의 `${DB_URL}` 로 흘러가
 * **개발 DB 에 `create-drop` 이 도는 사고**가 난다. 실제로 한 번 겪었다.
 *
 * 세션이 열릴 때 시스템 프로퍼티로 넣으면 슬라이스든 아니든 모든 컨텍스트가 같은 값을 본다.
 *
 * `@ServiceConnection` 으로 옮겨봤더니 **전체 테스트가 12초에서 43초가 됐다.**
 * Boot 가 컨텍스트를 닫을 때 컨테이너도 같이 내려서 다음 컨텍스트가 다시 띄우기 때문이다
 * (컨테이너 생성 2회 → 3회). 세션 수명에 묶어두면 그 일이 일어나지 않는다.
 *
 * 컨테이너는 JVM 당 하나, JVM 이 끝날 때 Testcontainers 의 ryuk 이 치운다.
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
        /** 운영과 같은 세대로 고정한다. `latest` 는 어느 날 올라가서 어제 통과하던 테스트를 깨뜨린다. */
        const val IMAGE = "postgres:16-alpine"

        @Volatile
        var started = false
    }
}
