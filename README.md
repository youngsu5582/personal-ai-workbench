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
| `cookie-secure`가 `true` | http인 localhost에서 로그인 쿠키가 실리지 않는다 |
| 로그 레벨이 `INFO` | 어디까지 진행됐는지 보이지 않는다 |

프로필을 켜면 `application-local.yaml`이 DB 주소까지 채우므로 별도 환경변수는 필요 없다.
비밀(`.env`)만 있으면 된다.

### 3. 테스트

```bash
./gradlew test
```

**Docker가 켜져 있어야 한다.** 테스트는 Testcontainers로 PostgreSQL을 띄우고 거기서 돈다.
H2를 쓰면 `jsonb`·`uuid`·`timestamp with time zone`·체크 제약이 흉내로만 검증된다.

컨테이너는 JUnit 세션마다 한 번만 뜬다(`PostgresTestDatabase`). 테스트 클래스는 아무것도 하지 않아도 된다.
같은 곳에서 **테스트가 `application.yaml` 을 상속하지 않게** 끊는다 — 그러지 않으면 `.env` 가 있는 기계에서만
개발 DB 주소와 실제 API 키가 테스트로 들어와 결과가 기계마다 갈린다.

스프링을 띄우는 테스트는 `IntegrationTest` 를 상속한다. 설정이 같아야 컨텍스트가 재사용되기 때문이다.
컨테이너·가짜 Provider·인메모리 보관소는 `SharedTestConfig` 한 곳에 있다 — 나중에 MQ·Redis 도 여기 붙는다.

```kotlin
class MyApiTest @Autowired constructor(...) : IntegrationTest()
```

상속하지 않는 것은 이유가 있을 때뿐이다 — 빈 DB 가 필요한 `SchemaMigrationTest`,
실제 포트가 필요한 `HandoffErrorStatusTest`, JSON 매핑만 보는 `ImageSizeTest`.
Gradle은 passed/skipped/failed 이벤트와 전체 예외를 출력한다.

### 4. 설정 파일

| 파일 | 무엇이 | 커밋 |
|---|---|---|
| `application.yaml` | 공통. 반드시 있어야 할 값에는 **기본값을 두지 않는다** | ✓ |
| `application-local.yaml` | 로컬의 비밀 아닌 값 (DB 주소, 쿠키, 로그 레벨) | ✓ |
| `.env` | 비밀만 (client secret, jwt secret, allowlist) | ✗ |

기본값을 두면 배포에서 환경변수를 빠뜨렸을 때 조용히 로컬 설정으로 뜬다.
특히 `jwt-secret`은 어디에도 기본값이 없다 — 두면 저장소에 공개된 키로 서명하게 된다.

`local` 프로필은 `bootRun`만 켠다. jar로 뜨는 배포에는 붙지 않으므로,
환경변수가 없으면 로컬 값으로 뜨는 것이 아니라 기동에 실패한다.

`.properties`가 아니라 YAML을 쓰는 이유는 인코딩이다. `.properties`는 Java 명세상 ISO-8859-1이라
IDE가 한글 주석을 깨뜨리는데, YAML은 명세가 UTF-8이라 그 문제가 없다.

### 5. 스키마

스키마는 `src/main/resources/db/migration`이 정한다. 엔티티는 검증받는 쪽이다.

```
ddl-auto: validate   ← 어디서나. 로컬도 예외가 아니다
```

엔티티를 고쳤으면 마이그레이션을 **같은 커밋에** 넣는다. 빠뜨리면 기동이 실패하는데,
그게 배포에서 알게 되는 것보다 낫다.

`ddl-auto: update`가 못 하던 일이 정확히 사고가 나던 자리다 — 기존 행이 있는 테이블에
NOT NULL 컬럼 추가(WARN으로 삼킨다), 체크 제약 갱신(보지 않는다), 컬럼·제약 삭제(하지 않는다).

JSON 컬럼(`option`, `metadata`)에 저장되는 타입에 **필수 필드를 추가하는 것도 같은 일**이다.
스키마는 그대로라 `validate`는 통과하지만 기존 행을 못 읽게 된다. 이것도 마이그레이션이 필요하다.

jOOQ 코드는 이 마이그레이션에서 생성된다(`DDLDatabase`). 살아 있는 DB에 붙지 않으므로
빌드에 DB가 필요 없고, 생성된 코드가 정본과 어긋날 수 없다.

### 6. 로깅

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

### 7. 종료

```bash
docker compose down
```

데이터 볼륨까지 삭제하려면 별도로 `docker compose down -v`를 실행한다.
Garage 를 함께 띄웠다면 내릴 때도 `COMPOSE_PROFILES=s3` 를 준다 — 프로필에 속한 컨테이너는 그 변수가 있어야 대상에 잡힌다.

## S3 호환 보관소로 돌려보기

결과물은 기본적으로 로컬 디스크(`./var/assets`)에 쌓인다. S3 경로를 확인하려면 [Garage](https://garagehq.deuxfleurs.fr/) 를 띄운다. MinIO 대신 Garage 인 이유는 홈랩 규모를 겨냥해 만들어졌고 라이선스·배포 정책이 안정적이기 때문이다.

### 1. Garage 시작

```bash
COMPOSE_PROFILES=s3 docker compose up -d
```

`profiles: ["s3"]` 가 붙어 있어 **평소 `docker compose up -d` 에는 뜨지 않는다.**

### 2. 최초 1회 부트스트랩

Garage 는 분산 스토리지라 노드가 하나여도 **"이 노드에 얼마를 할당한다"(layout)를 선언해야** 데이터를 받는다. 이 단계를 빼면 컨테이너는 떠 있는데 저장이 실패한다.

```bash
export COMPOSE_PROFILES=s3
NODE=$(docker compose exec -T garage /garage node id -q | cut -d@ -f1)

docker compose exec -T garage /garage layout assign -z dc1 -c 1G "$NODE"
docker compose exec -T garage /garage layout apply --version 1
docker compose exec -T garage /garage bucket create workbench
docker compose exec -T garage /garage key import --yes -n app-key \
  GK00000000000000000000dev 0000000000000000000000000000000000000000000000000000000000000001
docker compose exec -T garage /garage bucket allow --read --write workbench --key app-key
```

> 명령을 변수(`G="docker compose exec …"`)로 줄여 쓰고 싶어지는데, **zsh 에서는 안 된다.**
> zsh 는 변수를 단어로 쪼개지 않아 전체가 명령 이름 하나로 취급된다. 길어도 그대로 친다.

볼륨을 지우지 않는 한 **다시 할 필요 없다.** `docker compose down -v` 로 볼륨까지 지웠다면 다시 친다.

> 자동화하지 않은 이유: Garage 이미지에 셸이 없어 init 컨테이너로 명령을 엮을 수 없다. 커스텀 이미지를 만드는 것보다, 평생 한 번인 작업을 문서로 두는 편이 낫다고 판단했다.
>
> `key import` 로 값을 정해 넣는다. `key create` 는 자격증명을 **만들어 돌려주므로** 출력을 받아 적어야 한다. 위 값은 **로컬 전용**이다.

### 3. `.env` 에 보관소 설정 추가

```properties
STORAGE_PROVIDER=s3
STORAGE_S3_BUCKET=workbench
STORAGE_S3_REGION=garage
STORAGE_S3_ENDPOINT=http://localhost:3900
STORAGE_S3_ACCESS_KEY=GK00000000000000000000dev
STORAGE_S3_SECRET_KEY=0000000000000000000000000000000000000000000000000000000000000001
STORAGE_S3_PATH_STYLE_ACCESS=true
```

`path-style-access` 가 필요한 이유는 가상 호스트 방식(`bucket.localhost`)을 풀어줄 DNS 가 로컬에 없기 때문이다. **실제 AWS S3 나 Cloudflare R2 로 갈 때는 `endpoint` 를 그쪽 주소로, `path-style-access` 를 `false` 로 바꾸면 된다 — 코드는 그대로다.**

`bucket` 이 비어 있으면 기동에 실패한다. 오타를 낸 `provider` 도 가능한 이름과 함께 거절당한다.

### 4. 확인

Garage 에는 웹 콘솔이 없다. 객체는 `mc` 나 AWS CLI 로 본다.

```bash
aws --endpoint-url http://localhost:3900 s3 ls s3://workbench --recursive
```

키 모양은 `users/{ownerUuid}/blobs/{ab}/{cd}/{sha256}.png` 다 — 자리를 내용이 정하므로 같은 바이트는 한 자리를 쓴다.

### 5. 다운로드가 달라진다

`GET /api/files/{uuid}` 는 주소도 인가도 그대로지만, **응답이 달라진다.**

| 보관소 | 응답 | 바이트가 지나는 길 |
|---|---|---|
| `local` | `200` + 이미지 | 보관소 → **앱** → 클라이언트 |
| `s3` | `302` + `Location` | 보관소 → 클라이언트 (**앱을 안 거친다**) |

```bash
curl -i -H "Authorization: Bearer $(./scripts/dev-token.sh)" http://localhost:8080/api/files/<uuid>
# HTTP/1.1 302
# Location: http://localhost:3900/workbench/users/…/blobs/…png?X-Amz-Signature=…&X-Amz-Expires=300
```

발급된 주소는 **그 자체가 통행증**이다. 그래서 소유권 확인은 발급 **전에** 끝나고(남의 파일이면 404), 수명이 짧다(`workbench.storage.presigned-url-ttl`, 기본 5분). 서명 없이 같은 객체를 부르면 보관소가 거부한다.

로컬 디스크로 되돌리려면 `.env` 의 `STORAGE_PROVIDER` 를 지우거나 `local` 로 바꾼다. **클라이언트는 아무것도 안 바꾼다.**

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

Flyway가 이미 스키마가 있는 DB를 V1 지점에서 시작하므로(`baseline-on-migrate`) 그냥 띄우면 된다.
`flyway_schema_history`에 BASELINE 한 줄이 생기고 V1은 다시 돌지 않는다.

`ddl-auto=update`가 지우지 못해 남은 것들(예: `users.external_subject`)은 그대로 있다.
`validate`는 여분 컬럼을 문제 삼지 않으므로 기동에는 영향이 없다. 깔끔하게 시작하려면 비우면 된다.

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
