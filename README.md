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

테스트는 기본적으로 H2를 사용해 빠르게 실행한다. 운영·개발 런타임 DB는 PostgreSQL이다.

### 4. 종료

```bash
docker compose down
```

데이터 볼륨까지 삭제하려면 별도로 `docker compose down -v`를 실행한다.

## 현재 구현 범위

- `AuthenticatedPrincipal`
- `OwnerContext`
- `User` entity/repository
- `default-user` seed 예정
- GenerationJob/API/Provider/Worker는 다음 단계
