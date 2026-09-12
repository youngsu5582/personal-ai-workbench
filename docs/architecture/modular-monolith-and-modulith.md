# Modular Monolith와 Spring Modulith

## 한 줄 결론

- **Modular Monolith**는 하나의 애플리케이션으로 배포하되 내부를 기능별 모듈로 나누는 아키텍처 스타일이다.
- **Spring Modulith**는 Spring 애플리케이션의 패키지 경계를 분석하고, 모듈 간 의존성을 테스트하며, 애플리케이션 이벤트와 문서화를 지원하는 프레임워크다.

즉, Modular Monolith가 **설계 방향**이라면 Spring Modulith는 그 방향을 **검증하는 도구**다. Spring Modulith를 사용한다고 자동으로 좋은 모듈 구조가 만들어지는 것은 아니다.

## 1. 세 가지 구조를 비교하기

| 구조 | 배포 단위 | 데이터베이스 | 코드 경계 | 적합한 시점 |
|---|---|---|---|---|
| 단일 모놀리스 | 하나 | 보통 하나 | 약할 수 있음 | 빠른 초기 개발 |
| 모듈러 모놀리스 | 하나 | 하나 또는 제한된 공유 | 패키지·API로 강제 | 현재 Workbench |
| 마이크로서비스 | 여러 개 | 서비스별 분리 권장 | 네트워크 경계 | 독립 배포·확장이 실제로 필요할 때 |

모듈러 모놀리스의 장점은 요청 한 번으로 배포할 수 있는 단순함을 유지하면서도, 나중에 분리할 수 있는 **변경 경계**를 만든다는 점이다.

반대로 패키지만 나누고 모든 모듈이 서로의 Entity와 Repository를 자유롭게 참조하면, 이름만 모듈러 모놀리스인 결합된 모놀리스가 된다.

## 2. 현재 프로젝트의 모듈 후보

```text
dev.joyson.aiworkbench
├── ownership       인증 주체에서 소유자 컨텍스트를 해석
├── user            User Entity와 사용자 저장·조회
├── generation      GenerationJob과 생성 유스케이스
├── asset           결과 이미지와 저장소
└── provider        외부 AI Provider adapter
```

처음부터 모든 모듈을 만들 필요는 없다. 현재는 실제 코드가 있는 `ownership`과 `user`를 모듈로 인식시키고, `generation`이 추가될 때 경계를 확장한다.

## 3. Spring Modulith가 하는 일

### 자동으로 발견하는 것

Spring Modulith는 애플리케이션의 기준 패키지 아래에 있는 직접 하위 패키지를 Application Module 후보로 분석한다.

```text
dev.joyson.aiworkbench.user
 dev.joyson.aiworkbench.ownership
```

현재 프로젝트에서는 `user`와 `ownership`이 이 방식으로 발견된다.

### 검증하는 것

`ApplicationModules.verify()`를 실행하면 모듈 간 의존성이 정의된 모듈 구조와 맞는지 검사한다. 예를 들어 `generation`이 `user`의 공개 경계를 통하지 않고 내부 구현을 직접 참조하도록 구조를 바꾸면, 모듈 검증 테스트에서 문제를 발견할 수 있다.

### 아직 하지 않는 것

Spring Modulith가 다음을 자동으로 해결해 주는 것은 아니다.

- 도메인 경계를 대신 결정하기
- 잘못된 Entity 설계를 수정하기
- 모든 트랜잭션 문제 해결하기
- 외부 Provider 장애·재시도 정책 설계하기
- 마이크로서비스로 자동 분리하기

## 4. 현재 추가한 기초 setup

### Gradle 의존성

`build.gradle.kts`에 Spring Modulith BOM과 core/test starter를 추가했다.

```kotlin
dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.1.1")
    }
}

implementation("org.springframework.modulith:spring-modulith-starter-core")
testImplementation("org.springframework.modulith:spring-modulith-starter-test")
```

Spring Boot `4.1.1`과 호환되는 Spring Modulith `2.1.1`을 사용한다. BOM을 사용하므로 관련 모듈의 버전을 각각 적지 않는다.

### 모듈 검증 테스트

`src/test/kotlin/dev/joyson/aiworkbench/ModularityTest.kt`에 다음 검증을 추가했다.

```kotlin
class ModularityTest {
    @Test
    fun `애플리케이션 모듈 간 의존성 경계가 유효하다`() {
        ApplicationModules
            .of(PersonalAiWorkbenchApplication::class.java)
            .verify()
    }
}
```

이 테스트는 비즈니스 기능 테스트와 목적이 다르다.

- UserRepositoryTest: User 저장 규칙이 맞는가?
- OwnerContextTest: 소유자 해석 규칙이 맞는가?
- ModularityTest: 패키지 모듈 경계가 깨지지 않았는가?

현재 Dockerized Gradle 전체 테스트에서 이 검증은 통과했다.

## 5. 모듈 간 코드를 어떻게 사용해야 하나

### 좋은 방향

```text
generation.ApplicationService
    → user의 공개 사용자 조회 기능
    → ownership의 공개 OwnerContext
    → generation 내부 Domain 규칙
    → provider port
```

`generation`이 `user.User`의 내부 필드를 여기저기 읽거나 `UserRepository`를 직접 호출하는 대신, 사용자 조회에 필요한 공개 기능을 작은 API로 제공하는 방향이다.

### 피할 방향

```kotlin
// generation 모듈에서 user Entity의 내부 상태를 직접 조작
user.status = UserStatus.DISABLED
userRepository.save(user)
```

이렇게 하면 사용자 상태 변경 규칙의 소유자가 사라진다. 변경 의도가 User 모듈에 있다면 User 모듈의 서비스나 명시적인 도메인 메서드를 통해야 한다.

## 6. Generation 모듈이 추가될 때의 구조

```text
generation/
├── api/
│   ├── GenerationJobController.kt
│   └── GenerationJobResponse.kt
├── application/
│   ├── CreateGenerationJobService.kt
│   └── GetGenerationJobService.kt
├── domain/
│   ├── GenerationJob.kt
│   ├── JobStatus.kt
│   └── ImageGenerationProvider.kt
└── infrastructure/
    ├── JpaGenerationJobRepository.kt
    └── DirectHttpImageGenerationProvider.kt
```

각 디렉터리의 책임은 다음과 같다.

| 영역 | 책임 | 알면 안 되는 것 |
|---|---|---|
| `api` | HTTP 입력·응답 변환 | Provider payload와 DB 세부사항 |
| `application` | 유스케이스·트랜잭션·실행 순서 | HTTP 전용 세부사항 |
| `domain` | 상태 전이·불변식·port | JPA·RestClient 구현 |
| `infrastructure` | JPA·HTTP·파일 저장 구현 | Controller 흐름 |

## 7. 이벤트는 언제 쓰나

Spring Modulith에는 모듈 간 애플리케이션 이벤트를 연결하는 기능도 있다.

예를 들어 다음 흐름은 이벤트 후보가 될 수 있다.

```text
GenerationJobSucceeded
        ↓
Asset 모듈이 결과를 저장
        ↓
Usage 모듈이 사용량 기록
```

하지만 현재 단계에서 이벤트를 먼저 도입하지 않는다. 먼저 `GenerationJob` 상태 전이와 Provider polling을 직접 구현해 실행 흐름을 이해한다. 이벤트가 필요한 이유가 생겼을 때 다음을 판단한다.

- 같은 트랜잭션에서 반드시 끝나야 하는가?
- 실패 시 재처리해야 하는가?
- 발행 순서와 중복 소비를 관리해야 하는가?
- 호출자와 수신자를 느슨하게 결합해야 하는가?

이 판단 없이 이벤트를 사용하면 흐름을 찾기 어려운 분산된 코드가 된다.

## 8. 단계별 적용 순서

### 지금

1. `user`, `ownership` 패키지를 기능 경계로 유지한다.
2. `ModularityTest`를 계속 전체 테스트에 포함한다.
3. 모듈 내부 Entity와 Repository를 다른 모듈에서 직접 사용하지 않는다.

### 다음 기능

1. `default-user seed`를 User 모듈 안에 구현한다.
2. `GenerationJob`은 generation 모듈의 소유로 만든다.
3. owner scope 조회는 Controller가 아니라 Application Service에서 조정한다.
4. Provider interface는 generation domain의 port로 둔다.

### 나중

1. 실제 의존성이 생겼을 때 모듈 공개 API를 더 좁힌다.
2. 필요하면 모듈별 허용 의존성을 명시한다.
3. 이벤트가 유용한 지점을 선택적으로 Spring Modulith 이벤트로 전환한다.
4. 배포·확장 요구가 생긴 모듈만 별도 프로세스로 분리할지 검토한다.

## 9. 기억할 기준

- 모듈은 폴더 이름이 아니라 **변경 이유와 책임의 경계**다.
- Spring Modulith는 경계를 만들어 주는 마법이 아니라 **경계가 깨졌는지 알려주는 안전망**이다.
- Entity를 공유하는 것보다 의미 있는 공개 기능을 공유한다.
- 이벤트는 흐름을 숨기기 위해 쓰지 말고, 결합도를 낮출 명확한 이유가 있을 때 쓴다.
- 현재 프로젝트에서는 모듈러 모놀리스로 시작하고, Spring Modulith는 검증 도구로 작게 사용한다.

## 참고 링크

- [Spring Modulith 공식 문서](https://docs.spring.io/spring-modulith/reference/)
- [Spring Modulith GitHub](https://github.com/spring-projects/spring-modulith)
- [Spring Boot 공식 문서 · Structuring Your Code](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)
