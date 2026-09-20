package dev.joyson.aiworkbench

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * 스프링을 띄워야 하는 테스트의 공통 진입점.
 *
 * **설정을 한 가지로 묶는 것이 목적이다.** 스프링은 설정이 같은 테스트끼리만 컨텍스트를 재사용하므로,
 * 클래스마다 `@Import` 를 조금씩 다르게 붙이면 그만큼 컨텍스트가 늘어난다.
 * 슬라이스(`@DataJpaTest` 등)를 쓰지 않는 이유도 같다 — 슬라이스 조합 하나가 곧 컨텍스트 하나다.
 *
 * `@Transactional` 은 슬라이스가 해주던 일을 대신한다. 컨텍스트를 공유하면 스키마를 다시 만들 기회가
 * 없으므로, 테스트마다 되돌리지 않으면 데이터가 클래스를 넘어 쌓인다.
 * 커밋된 상태를 봐야 하는 테스트는 `@Transactional(propagation = NOT_SUPPORTED)` 로 빠진다.
 *
 * 자기만의 DB 나 설정이 필요한 테스트는 이것을 상속하지 않는다 — 예를 들어 [SchemaMigrationTest] 는
 * **빈 DB** 가 검증 대상이라 자기 컨테이너를 쓴다.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(SharedTestConfig::class)
@Transactional
abstract class IntegrationTest
