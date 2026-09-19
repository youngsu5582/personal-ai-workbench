package dev.joyson.aiworkbench

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test

/**
 * 마이그레이션이 만든 스키마와 엔티티 매핑이 서로 맞는지 본다.
 *
 * 이 테스트만 진짜 PostgreSQL(Testcontainers)을 쓴다. 나머지는 H2 라 여기서 볼 것들 —
 * `jsonb`·`uuid`·`timestamp with time zone`·체크 제약 — 이 그대로 검증되지 않는다.
 *
 * 검증 방법은 **기동 그 자체**다. Flyway 가 빈 DB 에 V1 을 걸고, 이어서 Hibernate 가
 * `validate` 로 엔티티와 대조한다. 어긋나면 컨텍스트가 뜨지 않는다.
 * 그래서 단언문이 없다 — 뜨면 맞는 것이다.
 */
@ActiveProfiles("test")
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        // 마이그레이션이 실제로 걸렸는지(baseline 으로 건너뛴 게 아닌지) 로그로 보여야 한다.
        // 통과만으로는 둘을 구분할 수 없다 — 이미 스키마가 있는 DB 에서도 validate 는 통과한다.
        "logging.level.org.flywaydb=INFO",
    ],
)
@Import(TestcontainersConfiguration::class)
class SchemaMigrationTest {

    @Test
    fun `마이그레이션이 만든 스키마를 엔티티 매핑이 검증한다`() = Unit
}
