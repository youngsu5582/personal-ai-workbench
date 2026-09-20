package dev.joyson.aiworkbench

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 자기만의 DB 가 필요한 곳에 쓴다.
 *
 * 대부분의 테스트는 [PostgresTestDatabase] 가 띄운 공용 컨테이너를 쓴다. 이 설정을 import 하면
 * `@ServiceConnection` 이 그 접속 정보를 덮어 **빈 DB** 를 받는다 —
 * 마이그레이션처럼 "아무것도 없는 상태에서 시작" 이 검증 대상인 경우가 그렇다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	fun postgresContainer(): PostgreSQLContainer {
		// 공용 컨테이너와 같은 세대로 맞춘다.
		return PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
	}

}
