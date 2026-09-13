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

**Run/Debug Configurations → Active profiles 에 `local` 을 넣는다.**

`./gradlew bootRun`은 이 프로필을 자동으로 켜지만, IntelliJ는 main 클래스를 직접 실행해서 붙지 않는다.
없으면 이렇게 된다.

| 없을 때 | 결과 |
|---|---|
| `ddl-auto`가 `validate` | 스키마를 만들지 않아 기동에 실패한다 |
| `cookie-secure`가 `true` | http인 localhost에서 로그인 쿠키가 실리지 않는다 |

프로필을 켜면 `application-local.yaml`이 DB 주소까지 채우므로 별도 환경변수는 필요 없다.
비밀(`.env`)만 있으면 된다.

### 3. 테스트

```bash
./gradlew test
```

테스트는 `test` 프로필을 사용하며 H2로 빠르게 실행한다. Gradle은 passed/skipped/failed 이벤트와 전체 예외를 출력한다.

### 4. 설정 파일

| 파일 | 무엇이 | 커밋 |
|---|---|---|
| `application.yaml` | 공통. 반드시 있어야 할 값에는 **기본값을 두지 않는다** | ✓ |
| `application-local.yaml` | 로컬의 비밀 아닌 값 (DB 주소, `ddl-auto`, 로그 레벨) | ✓ |
| `.env` | 비밀만 (client secret, jwt secret, allowlist) | ✗ |

기본값을 두면 배포에서 환경변수를 빠뜨렸을 때 조용히 로컬 설정으로 뜬다.
특히 `jwt-secret`은 어디에도 기본값이 없다 — 두면 저장소에 공개된 키로 서명하게 된다.

`local` 프로필은 `bootRun`만 켠다. jar로 뜨는 배포에는 붙지 않으므로,
환경변수가 없으면 로컬 값으로 뜨는 것이 아니라 기동에 실패한다.

`.properties`가 아니라 YAML을 쓰는 이유는 인코딩이다. `.properties`는 Java 명세상 ISO-8859-1이라
IDE가 한글 주석을 깨뜨리는데, YAML은 명세가 UTF-8이라 그 문제가 없다.

### 5. 로깅

Spring Boot 기본 SLF4J/Logback을 사용한다. 애플리케이션 패키지는 `local` 프로필에서 `DEBUG`,
그 외에는 `INFO`로 출력한다.

```kotlin
private val log = LoggerFactory.getLogger(MyService::class.java)
log.info("job submitted: jobId={}", jobId)
```

환경변수로 레벨을 조절할 수 있다.

```bash
LOG_LEVEL_ROOT=INFO LOG_LEVEL_APP=TRACE ./gradlew bootRun
```

### 6. 종료

```bash
docker compose down
```

데이터 볼륨까지 삭제하려면 별도로 `docker compose down -v`를 실행한다.

## 도메인 문서

구현 시 사용하는 도메인 용어·불변식·Aggregate 경계는 [`docs/domain/domain-model.md`](docs/domain/domain-model.md)에 정리한다.

## 아키텍처 학습 문서

모듈러 모놀리스·Spring Modulith·DDD·모듈 간 의존성 원칙은 [`docs/architecture/modular-monolith-and-modulith.md`](docs/architecture/modular-monolith-and-modulith.md)에 정리한다.

현재는 `ApplicationModules.verify()` 기반의 모듈 경계 검증 테스트를 사용한다.

## 인증

Google 로그인으로 사용자를 확인하고, **그 다음부터는 이 서비스가 직접 발급한 JWT** 로 API를 호출한다.
Google은 로그인 순간에만 관여하고 이후 흐름에서 빠진다.

### 1. Google OAuth client 발급

Google Cloud Console > APIs & Services > Credentials > OAuth client ID (Web application).
Authorized redirect URI에 아래를 **정확히** 등록한다.

```text
http://localhost:8080/login/oauth2/code/google
```

### 2. 환경변수

```bash
cp .env.example .env   # .env 는 gitignore 된다
```

```bash
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
AUTH_JWT_SECRET=$(openssl rand -base64 48)   # HS256, 최소 32바이트
AUTH_ALLOWED_EMAILS=me@example.com           # 비우면 누구나 로그인된다
```

`AUTH_ALLOWED_EMAILS`를 비운 채로 외부에 노출하면 구글 계정이 있는 누구나 로그인해 Provider 크레딧을 쓴다.

### 3. 확인

`http://localhost:8080` 의 Auth Playground에서 로그인 → 발급된 토큰의 claim 확인 → `GET /api/me` 호출까지 한 페이지에서 된다.

```text
브라우저 → /oauth2/authorization/google
        → Google 동의
        → /login/oauth2/code/google   (Spring이 code 교환·ID token 검증·userinfo 조회)
        → (issuer, subject) 로 User 해석 또는 신규 등록
        → 내 access token 발급 → 60초짜리 HttpOnly 쿠키로 전달
        → 페이지가 GET /auth/handoff 로 한 번 교환 (이후 토큰은 메모리에만)
        → Authorization: Bearer <token> 으로 /api/me
```

토큰은 URL에도 localStorage에도 저장하지 않는다. 새로고침하면 사라지는 것이 정상이다.

### 기존 DB를 쓰고 있었다면

`users.external_subject` 에 걸려 있던 단일 컬럼 unique 인덱스는 `ddl-auto=update` 가 지우지 못한다.
로컬 DB를 한 번 비우고 시작한다.

```bash
docker compose down -v && docker compose up -d postgres
```

## 현재 구현 범위

- `User` / `UserIdentity` — 한 사용자에 여러 외부 인증 주체를 연결 (`UNIQUE(issuer, subject)`)
- `UserRegistry` — user 모듈의 공개 경계 (조회·등록·연결·해제)
- Google OIDC 로그인과 JIT provisioning, 이메일 allowlist
- 자체 access token 발급(HS256) 및 `/api/**` Bearer 검증
- `OwnerContext` — 인증 방식과 무관하게 소유자를 해석
- Auth Playground 샘플 페이지

다음 단계:

1. refresh token (rotation + reuse detection) 과 로그아웃
2. GitHub provider 추가 — OIDC가 아니므로 issuer를 상수로 정해야 한다
3. 계정 연결 (로그인 상태에서 두 번째 provider 붙이기)
4. `GenerationJob` 모델과 owner scope 조회
