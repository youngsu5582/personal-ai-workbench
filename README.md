# Personal AI Workbench

외부 AI Provider의 비동기 이미지 생성 작업을 Job으로 관리하는 개인용 AI 서비스다.

## 로컬 개발

### 1. PostgreSQL 시작

```bash
docker compose up -d postgres
```

기본 연결 정보:

```text
host: localhost
port: 5433
database: personal_ai_workbench
username: workbench
password: workbench
```

### 2. IntelliJ 실행

- Project SDK: JDK 21
- Gradle JVM: JDK 21
- Build and run using: Gradle
- Gradle distribution: Gradle wrapper

`PersonalAiWorkbenchApplication`을 실행하면 된다. 기본 `application.properties`가 위 PostgreSQL에 연결한다.

환경변수를 사용해 연결 정보를 바꿀 수도 있다.

```bash
DB_URL=jdbc:postgresql://localhost:5433/personal_ai_workbench \
DB_USERNAME=workbench \
DB_PASSWORD=workbench \
./gradlew bootRun
```

### 3. 테스트

```bash
./gradlew test
```

테스트는 `test` 프로필을 사용하며 H2로 빠르게 실행한다. Gradle은 passed/skipped/failed 이벤트와 전체 예외를 출력한다.

### 4. 로깅

Spring Boot 기본 SLF4J/Logback을 사용한다. 애플리케이션 패키지는 기본 `DEBUG`, 나머지는 `INFO`로 출력한다.

```kotlin
private val log = LoggerFactory.getLogger(MyService::class.java)
log.info("job submitted: jobId={}", jobId)
```

환경변수로 레벨을 조절할 수 있다.

```bash
LOG_LEVEL_ROOT=INFO LOG_LEVEL_APP=TRACE ./gradlew bootRun
```

### 5. 종료

```bash
docker compose down
```

데이터 볼륨까지 삭제하려면 별도로 `docker compose down -v`를 실행한다.

## 도메인 문서

구현 시 사용하는 도메인 용어·불변식·Aggregate 경계는 [`docs/domain/domain-model.md`](docs/domain/domain-model.md)에 정리한다.

## 아키텍처 학습 문서

모듈러 모놀리스·Spring Modulith·DDD·모듈 간 의존성 원칙은 [`docs/architecture/modular-monolith-and-modulith.md`](docs/architecture/modular-monolith-and-modulith.md)에 정리한다.

현재는 `ApplicationModules.verify()` 기반의 모듈 경계 검증 테스트를 사용한다.

## 현재 구현 범위

- `AuthenticatedPrincipal`
- `OwnerContext`
- `User` entity/repository
- `default-user` seed 예정
- GenerationJob/API/Provider/Worker는 다음 단계
