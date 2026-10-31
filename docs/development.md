# 개발 가이드

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

```bash
BASE_URL=http://localhost:8080 ./scripts/file.sh --job <job-uuid>
```

보관소에 따라 응답이 갈리는 것을 그대로 보여준다.

```
  파일 4e1d2d4e-…
     앱 응답    302 → http://localhost:3900/workbench/users/…/5c7d9395….png  [서명됨, 900초]
     내려받기   HTTP 200  2,277,138 bytes  image/png
     무결성     sha256 일치 (5c7d9395689f04a7…)
     토큰 없이  HTTP 401
     previewUrl http://localhost:3900/workbench/users/…/5c7d9395….png  [서명됨, 900초]
     헤더 없이  HTTP 200  2,277,138 bytes  image/png
```

마지막 두 줄이 `<img src>` 가 되는지를 답한다 — **헤더 없이 200** 이면 그려진다. `local` 로 띄우면 `앱 응답 200`, `previewUrl 없음` 이 나온다.

무결성 줄은 키가 내용 주소라서 공짜로 얻는 것이다. 파일명이 곧 sha256 이므로 받은 바이트와 대조하면 끝난다.

Garage 에는 웹 콘솔이 없다. 객체는 `mc` 나 AWS CLI 로 본다.

```bash
aws --endpoint-url http://localhost:3900 s3 ls s3://workbench --recursive
```

키 모양은 `users/{ownerUuid}/blobs/{ab}/{cd}/{sha256}.png` 다 — 자리를 내용이 정하므로 같은 바이트는 한 자리를 쓴다.

### 5. 다운로드가 달라진다

`GET /api/files/{sha256}` 는 주소도 인가도 그대로지만, **응답이 달라진다.**

| 보관소 | 응답 | 바이트가 지나는 길 |
|---|---|---|
| `local` | `200` + 이미지 | 보관소 → **앱** → 클라이언트 |
| `s3` | `302` + `Location` | 보관소 → 클라이언트 (**앱을 안 거친다**) |

```bash
curl -i -H "Authorization: Bearer $(./scripts/dev-token.sh)" http://localhost:8080/api/files/<sha256>
# HTTP/1.1 302
# Location: http://localhost:3900/workbench/users/…/blobs/…png?X-Amz-Signature=…&X-Amz-Expires=300
```

목록(`GET /api/jobs/{uuid}`)의 파일마다 `previewUrl` 도 함께 온다. 이건 **서명된 절대 주소**라 `<img src>` 에 그대로 넣으면 그려진다 — `url` 은 `Authorization` 헤더가 필요해서 `<img>` 로는 못 쓴다.

| 필드 | 수명 | 쓰임 |
|---|---|---|
| `url` | 안 죽는다 | 저장·북마크·스크립트. 302 로 서명 주소에 넘긴다 |
| `previewUrl` | 짧다(기본 15분) | `<img src>` 로 바로 그린다. 로컬 디스크면 `null` |

발급된 주소는 **그 자체가 통행증**이다. 그래서 소유권 확인은 발급 **전에** 끝나고(남의 파일이면 404), 수명이 짧다(`workbench.storage.presigned-url-ttl`, 기본 5분). 서명 없이 같은 객체를 부르면 보관소가 거부한다.

로컬 디스크로 되돌리려면 `.env` 의 `STORAGE_PROVIDER` 를 지우거나 `local` 로 바꾼다. **클라이언트는 아무것도 안 바꾼다.**

## 지표 보기 (Prometheus · Grafana)

앱이 management 포트(8081)로 내보내는 지표를 Prometheus 가 15초마다 긁고, Grafana 가 그것과 개발 DB 를 함께 그린다.

### 1. 시작

```bash
COMPOSE_PROFILES=observability docker compose up -d prometheus grafana
./gradlew bootRun
```

`profiles: ["observability"]` 라 **평소 `docker compose up -d` 에는 뜨지 않는다.** 개발 DB 는 평소처럼 띄워 둔다.

### 2. 열기

| 주소 | 무엇 |
|---|---|
| http://localhost:3000 | Grafana. 로그인 없이 관리자로 들어간다 — 그래서 이 머신(127.0.0.1)에서만 열린다. **Workbench** 대시보드가 미리 들어 있다 |
| http://localhost:9090/targets | Prometheus 가 앱을 긁고 있는지. `workbench` 가 `UP` 이어야 한다 |
| http://localhost:8081/actuator/prometheus | 앱이 내보내는 원본. 그 순간의 값만 있다 |

대시보드는 다섯 줄이다.

| 줄 | 무엇을 | 출처 |
|---|---|---|
| 한눈에 | 앱 상태 · 가동 시간 · 진행 중 호출 · 기다리는/처리 중 Task · 최근 24시간 호출 | 둘 다 |
| Provider — 지금 | 성공률 · 실패 종류 · p95 · 가장 오래 돌고 있는 호출 · 결과별 호출 수 | 앱 지표. 앱을 다시 띄우면 0 부터 |
| Provider — 기록 | Provider · 모델별 표 · 실패 사유 · 하루 단위 성공/실패 (최근 30일) | 원장 `provider_calls` |
| 작업 | 상태별 Task · 가장 오래 기다린 Task · 하루 단위 Job · 최근 실패한 Task | DB |
| 앱 런타임 (접혀 있다) | HTTP · error/warn 로그 수 · DB 커넥션 · 힙 · GC · CPU | 앱 지표 |

Provider 지표는 **첫 호출이 끝난 뒤에** 생긴다. 앱을 막 띄웠을 때 "아직 호출 없음" 으로 보이는 것은 정상이다.

### 3. 대시보드를 고칠 때

원본은 `docker/grafana/dashboards/workbench.json` 이다. **화면에서 고친 것은 저장되지 않는다.** JSON 을 고친 뒤 `docker restart personal-ai-workbench-grafana` 로 다시 읽힌다 — Rancher Desktop 에서는 파일이 바뀌어도 Grafana 가 저절로 다시 읽지 않는다. 데이터소스는 `docker/grafana/provisioning/datasources/workbench.yaml` 에 uid 로 고정돼 있고, 대시보드가 그 uid 로 가리킨다.

### 4. 앱과 DB 를 호스트로 부르는 이유

앱은 호스트에서 bootRun 으로 뜨고, 개발 DB 는 다른 워크트리의 compose 에서 떠 있을 수도 있다. 그래서 둘 다 컨테이너 이름이 아니라 `host.docker.internal` 로 부른다. Rancher Desktop 은 이 이름을 호스트로 풀어 주지만, Linux 의 Docker 라면 두 서비스에 `extra_hosts: ["host.docker.internal:host-gateway"]` 가 필요하다.

### 5. 종료

```bash
COMPOSE_PROFILES=observability docker compose stop prometheus grafana
```

`docker compose down -v` 는 **개발 DB 볼륨까지** 지운다. 지표만 비우려면 `docker compose rm -sf prometheus grafana` 뒤에 그 두 볼륨만 지운다.

## 이미지 투 이미지

본인이 생성한 결과 파일이나 직접 올린 파일을 입력으로 지정해 다시 생성한다. 현재 입력은 1장까지다.
요청 종류는 글만으로 만들 때와 같은 `"type": "image-generate"`이고, `sources`가 있으면 그 파일을 참조한다. 없거나 비어 있으면 글만으로 만든다.
`sources[]`는 `{"type": "generated", "sha256": "<결과 파일 해시>"}` 또는 `{"type": "uploaded", "sha256": "<업로드 파일 해시>"}`다.
올린 파일은 처음 입력으로 쓰일 때 형식을 확인하며, 맞지 않으면 400이다.

실행 중인 서버에는 다음 스크립트로 요청한다. 입력 해시는 `GET /api/jobs/{uuid}` 응답의
`tasks[].files[].sha256`에서 확인한다. Job 과 Task 식별자는 UUID 를 유지한다.

```bash
./scripts/i2i.sh <생성-결과파일-sha256> "수채화로 바꿔줘"
./scripts/i2i.sh <생성-결과파일-sha256> "수채화로 바꿔줘" 2 gpt-image-2
RATIO=16:9 RESOLUTION=2k QUALITY=high ./scripts/i2i.sh <생성-결과파일-sha256> "수채화로"
```

기본 모델은 `gpt-image-2`, 작업 수는 1, 크기는 `1:1 / 1k`, 품질은 `auto`다.
`BASE_URL`, `USER_UUID`를 지정할 수 있고, `ACCESS_TOKEN`이 없으면 `dev-token.sh`로 인증한다.
접수 후 출력되는 `job.sh` 명령으로 진행 상황을 조회한다.

```http
POST /api/jobs
Authorization: Bearer <token>
Content-Type: application/json

{
  "option": {
    "type": "image-generate",
    "prompt": "수채화로 바꿔줘",
    "sources": [{"type": "generated", "sha256": "<generated-file-sha256>"}],
    "size": {"type": "pixels", "width": 1024, "height": 1024},
    "quality": "high"
  },
  "model": "gpt-image-2",
  "taskCount": 1
}
```

`202 Accepted`의 Job UUID로 `GET /api/jobs/{uuid}`에서 결과를 조회한다.
존재하지 않거나 다른 사용자 소유인 입력은 접수 시 거절하며, 워커 실행 시에도 소유권을 확인한다.

S3 호환 보관소에서는 워커가 실행·재시도마다 새 presigned GET URL을 발급하고, OpenAI의
`POST /v1/images/edits`에 JSON `images[].image_url`로 전달한다. 이미지 바이트는 보관소에서
Provider로 직접 이동하며, URL은 Job 옵션에 저장하거나 로그에 출력하지 않는다.
URL 발급자가 없는 로컬 저장소에서는 앱이 바이트를 읽어 기존 multipart 전송을 사용한다.

## 텍스트 생성

글을 만든다. 요청 종류는 `"type": "text-generate"` 하나이고, `output` 을 빼면 형식 없는 글을,
`{"type": "json-schema", "schema": {...}}` 를 주면 그 스키마를 따르는 JSON 객체를 받는다.
`instructions` 는 모델에게 주는 역할·규칙(system 프롬프트)이고 선택이다.
`effort` 는 답하기 전에 얼마나 생각할지(`none` · `low` · `medium` · `high` · `xhigh` · `max`)이고, 빼면 모델 기본값(대개 `medium`)이다.
생각한 만큼이 출력 토큰으로 과금되므로 간단한 일에는 낮춘다.

```bash
./scripts/text.sh "이 글을 세 줄로 요약해: ..."
./scripts/text.sh "제목을 지어줘" 2 gpt-6.1-sol
INSTRUCTIONS="너는 기술 블로그 편집자다" ./scripts/text.sh "제목을 지어줘"
EFFORT=low ./scripts/text.sh "이 글의 태그 다섯 개"
SCHEMA='{"type":"object","properties":{"tags":{"type":"array","items":{"type":"string"}}},"required":["tags"],"additionalProperties":false}' \
  ./scripts/text.sh "이 글의 태그를 골라: ..."
```

기본 모델은 `gpt-6-luna`, 작업 수는 1이다. 이미지와 같은 비동기 Job 이라 접수 후 출력되는 `job.sh` 명령으로 결과를 본다.

```http
POST /api/jobs
Authorization: Bearer <token>
Content-Type: application/json

{
  "option": {
    "type": "text-generate",
    "prompt": "이 글의 태그를 골라: ...",
    "instructions": "너는 기술 블로그 편집자다",
    "output": {
      "type": "json-schema",
      "schema": {
        "type": "object",
        "properties": {"tags": {"type": "array", "items": {"type": "string"}}},
        "required": ["tags"],
        "additionalProperties": false
      }
    }
  },
  "model": "gpt-6-luna",
  "taskCount": 1
}
```

결과는 `GET /api/jobs/{uuid}` 의 `tasks[].texts[].content` 에 바로 실린다. 형식 없는 글이면 문자열,
스키마를 줬으면 그 스키마를 따르는 객체다. 이미지 Task 의 `texts` 와 글 Task 의 `files` 는 빈 목록이다.

- **스키마의 키 순서가 답의 키 순서다.** 근거를 먼저 쓰게 하려면 `reasoning` 을 `answer` 앞에 둔다.
  그 순서를 지키려고 스키마는 원문 문자열로 저장되며, 조회 응답의 `option.output.schema` 도 문자열로 돌아온다.
- OpenAI 는 strict 모드로 부른다. 모든 필드가 `required` 에 있고 객체마다 `additionalProperties: false` 여야 한다.
  어기면 접수는 되지만 Task 가 저쪽의 400 사유와 함께 실패한다.
- 모델이 거절하거나 출력 한도에 걸려 잘리면 Task 를 재시도 없이 실패로 닫는다. 그 호출도 과금됐으므로 지출은 사용량과 함께 성공 호출로 적힌다.
- 이미지 모델로 글을, 텍스트 모델로 이미지를 요청하면 접수에서 400 이다.
- effort 는 모델마다 받는 값이 다르다. `gpt-6-astra` 는 `none` 을, `gpt-6.1-sol` 은 `none` 을 받지 않는다. 접수는 되고 Task 가 저쪽 400 사유로 실패한다.

URL은 **Provider에서 접근할 수 있는 보관소 주소**여야 한다. 로컬 Garage의 `localhost:3900`은
외부 OpenAI에서 접근할 수 없다. 실제 URL 입력을 사용하려면 앱과 Provider 모두 접근 가능한
보관소 endpoint를 설정한다. 실제 OpenAI URL 편집 호출은 아직 미검증이며, 전송 계약은 대역 테스트로 검증한다.
[OpenAI 공식 편집 API 계약](https://developers.openai.com/api/reference/resources/images/methods/edit)

## 판정

근거(글 · 이미지)를 정해 둔 질문으로 판정한다. 요청 종류는 `"type": "decision"` 이다.
질문은 세 종류이고, 종류마다 답의 모양이 다르다.

| 질문 | 받는 답 |
|---|---|
| `predicate` | 참일 확률 `probability` (0 ~ 1) |
| `choice` | `choices` 의 `value` 중 하나인 `choice` 와 `confidence` |
| `score` | `levels` 에 대한 `score` 와 `confidence` |

```bash
./scripts/decision.sh "다음 달부터 구독을 해지하고 싶어요"
./scripts/decision.sh "사진을 보고 파손 여부를 판단해줘" <generated-file-sha256>
SOURCE_TYPE=uploaded ./scripts/decision.sh "파손 여부" <uploaded-file-sha256>
```

```http
POST /api/jobs
Authorization: Bearer <token>
Content-Type: application/json

{
  "option": {
    "type": "decision",
    "input": [
      {"type": "text", "text": "다음 달부터 구독을 해지하고 싶어요"},
      {"type": "image", "source": {"type": "uploaded", "sha256": "<uploaded-file-sha256>"}}
    ],
    "questions": [
      {"type": "predicate", "name": "needs_review", "instructions": "사람의 확인이 필요한가?"},
      {"type": "choice", "name": "intent", "instructions": "주된 의도를 고른다",
       "choices": [{"value": "cancel", "description": "해지를 원한다"}, {"value": "question", "description": "정보를 묻는다"}]},
      {"type": "score", "name": "urgency", "instructions": "처리 긴급도",
       "levels": [{"label": "low", "description": "급하지 않다"}, {"label": "high", "description": "빨리 처리해야 한다"}]}
    ]
  },
  "model": "gpt-6-luna",
  "taskCount": 1
}
```

결과는 `GET /api/jobs/{uuid}` 의 `tasks[].decisions[].answers` 에 실린다.
답은 `name` 으로 질문을 가리킨다 — 순서는 Provider 가 준 그대로라 질문의 순서에 기대지 않는다.
각 답의 `type` 이 질문의 종류이고, 모델이 그 질문에 답하기를 거절했으면 `refusal` 이다.

- 한 호출이 질문을 모두 답하므로 `taskCount` 는 1 이어야 한다. 아니면 400 이다.
- 판정 모델이 아닌 모델로 판정을, 판정 모델로 다른 종류를 요청하면 접수에서 400 이다.
- 거절은 Task 의 실패가 아니다. 나머지 답과 함께 남는다.
- 이미지 근거는 이미지 생성의 참조와 같이 접수 때 소유를 확인한다. 주소로 넘겼다가 저쪽이 받아 가지 못하면 바이트로 한 번 더 보낸다.
- 요청 · 응답 본문을 실제 API 로 확인했는지는 `OpenAIDecisionModel` 의 표시가 말한다.

## 직접 업로드 URL 발급

S3 호환 보관소에서는 Bearer 토큰으로 `POST /api/uploads`를 호출한다.

```http
POST /api/uploads
Authorization: Bearer <token>
Content-Type: application/json

{"contentType":"image/png","contentLength":1024,"sha256":"<파일 SHA-256 소문자 hex 64자>"}
```

`200 OK` 응답:

```json
{
  "sha256": "<파일 SHA-256 소문자 hex 64자>",
  "uploadRequired": true,
  "url": "http://<storage>/<bucket>/users/<owner>/uploads/<fanout>/<hash>.png?<signature>",
  "method": "PUT",
  "headers": {
    "Content-Type": "image/png",
    "Content-Length": "1024",
    "x-amz-checksum-sha256": "<SHA-256 digest 의 Base64>",
    "x-amz-sdk-checksum-algorithm": "SHA256"
  }
}
```

이미 검증된 같은 파일이 있으면 `uploadRequired: false`, 기존 `sha256`, `url: null`, `method: null`, 빈 `headers`를 반환한다.
이때는 PUT을 생략하고 해시를 입력으로 사용한다. 아직 올라오지 않은 같은 파일은 같은 해시의 주소를 다시 발급한다.
`contentType`은 파일 이름의 확장자가 아니라 앞부분의 매직넘버로 정한다. 선언과 내용이 다르면 거절되고,
같은 내용은 형식을 바꿔 선언해도 다시 발급받을 수 없다(400).

반환된 URL로 파일 바이트를 PUT한다. 앱의 Bearer 토큰은 보관소에 보내지 않는다.
형식·크기·SHA-256은 서명에 포함돼 실제 요청과 같아야 한다. 체크섬 헤더 두 개도 응답 그대로 보낸다. 브라우저에서는 `Content-Length`를
직접 설정하지 않고 `File`/`Blob`을 본문으로 보내 브라우저가 계산하게 한다.
PNG·JPEG·WebP, 최대 20MiB를 허용하며, 잘못된 요청은 400, 미인증은 401,
직접 업로드를 지원하지 않는 로컬 보관소는 501이다. 중복 확인 중 보관소에 닿지 못하면 503이며 상태를 바꾸지 않는다.
수명은 `workbench.storage.presigned-url-ttl`을 따른다.

올린 파일은 `SOURCE_TYPE=uploaded ./scripts/i2i.sh <업로드 SHA-256> "<프롬프트>"`로 i2i 입력에 쓴다.
설계와 그 이유는 [`docs/architecture/direct-upload.md`](architecture/direct-upload.md)에 정리한다.

### 스크립트로 발급 → 업로드 확인

위의 Garage 부트스트랩과 `.env` 보관소 설정을 마친 후 앱을 실행한다.
`curl`과 `python3`가 필요하다. 이미지 파일 경로는 명령을 실행하는 디렉터리 기준이다.

```bash
./scripts/upload.sh ./image.png
BASE_URL=http://localhost:8099 ./scripts/upload.sh './images/my photo.jpg'
```

기본 인증은 `dev-token.sh`를 사용하므로, 먼저 한 번 로그인해 DB에 사용자를 등록한다.
특정 사용자는 `USER_UUID`로 지정하고, 이미 가진 토큰은 `ACCESS_TOKEN` 환경변수로 전달할 수 있다.
파일 헤더로 PNG·JPEG·WebP를 판별하고 크기·SHA-256을 계산한 뒤, `POST /api/uploads` 응답의 주소로
파일을 직접 PUT한다. 검증된 기존 파일이면 전송을 생략한다. 파일 해시·서명을 제외한 경로·각 HTTP 상태를 출력하며 실패하면 0이 아닌 코드로 종료한다.
Bearer 토큰은 앱에만 보낸다.

### 샘플 페이지에서 발급 → 업로드 확인

브라우저가 앱과 다른 origin의 보관소로 PUT하려면 **보관소 버킷의 CORS** 설정이 필요하다.
앱에 CORS를 추가하는 것으로는 해결되지 않는다.
[Garage도 PutBucketCors를 지원한다](https://github.com/deuxfleurs-org/garage/blob/main-v2/doc/book/reference-manual/s3-compatibility.md).

```bash
# 로컬 Garage에서는 설정에 사용하는 키에 버킷 owner 권한이 필요하다.
docker compose --profile s3 exec -T garage /garage bucket allow --owner workbench --key app-key
# AWS CLI·curl과 .env의 STORAGE_S3_* 설정을 사용한다.
./scripts/upload-cors.sh
# 앱을 다른 포트로 실행했다면 페이지 origin을 정확히 지정한다.
./scripts/upload-cors.sh http://localhost:8099
```

스크립트는 해당 origin의 PUT·Content-Type 허용 규칙을 추가한다. 동일 origin의 샘플 규칙만
갱신하고 다른 기존 규칙은 유지한다. 와일드카드 origin은 허용하지 않는다.
브라우저 preflight가 보내는 소문자 `content-type`·`x-amz-checksum-sha256`·`x-amz-sdk-checksum-algorithm`을 허용하며,
기존 CORS 설정을 사용 중이면 스크립트를 다시 실행한다. 설정 적용 후 실제 OPTIONS
요청까지 성공해야 스크립트가 성공으로 종료한다. 이 확인은 파일 PUT 권한·전송 성공과는 별개다.

앱의 `http://localhost:8080/`에서 Google 로그인 후 **이미지 직접 업로드** 영역에 파일을 선택하고
**URL 발급 후 업로드**를 누른다. 발급 단계·전송 진행률·보관소 응답을 확인할 수 있다.
`Content-Length`는 JavaScript에서 설정하지 않고 브라우저가 `File`의 크기로 계산한다.
화면의 이미지는 선택한 원본 미리보기이며, 업로드 성공은 보관소의 PUT 수락을 의미한다.
네트워크 오류가 나면 페이지 origin과 CORS 설정, 브라우저에서 접근 가능한 보관소 주소를 확인한다.

### 홈서버 K3s 배포

`deploy/overlays/home`은 Workbench Pod의 init container에서 Garage의 `PutBucketCors`를 실행한다.
Git에는 CORS method/header 정책만 두고, 배포별 public origin은 `runtime.env`의
`WORKBENCH_PUBLIC_ORIGIN`으로 주입한다. 따라서 특정 운영 도메인이 ConfigMap이나 Deployment
manifest에 들어가지 않고, Pod를 다시 만들 때마다 해당 환경의 정책으로 재조정된다.
이 init container가 사용할 S3 키에는 버킷 owner 권한이 필요하다. 아래의 `workbench-app`은
K3s 부트스트랩에서 `STORAGE_S3_ACCESS_KEY`를 만든 키 이름이다. 기존에 다른 키 이름을 사용했다면
그 키 이름을 지정한다.

`runtime.env`를 복사해 Secret을 만들기 전에 다음 항목도 환경에 맞게 채운다.

```text
WORKBENCH_PUBLIC_ORIGIN=<public Workbench origin, including https://>
```

```bash
sudo k3s kubectl -n workbench exec garage-0 -- \
  /garage bucket allow --read --write --owner workbench --key workbench-app
sudo k3s kubectl -n workbench rollout restart deployment/workbench
sudo k3s kubectl -n workbench logs deployment/workbench -c configure-garage-cors
```

로그에 `Garage CORS policy verified`가 나오고 Workbench rollout이 완료된 뒤,
브라우저가 실제로 접근하는 public file hostname에서 `OPTIONS` preflight도 확인한다.
다른 환경별 origin이 필요하면 해당 환경의 `WORKBENCH_PUBLIC_ORIGIN` Secret 값만 바꾼다.
`workbench-cors.yaml`에는 운영 주소를 추가하지 않는다.

## 도메인 문서

구현 시 사용하는 도메인 용어·불변식·Aggregate 경계는 [`docs/domain/domain-model.md`](domain/domain-model.md)에 정리한다.

## 아키텍처 학습 문서

모듈러 모놀리스·Spring Modulith·DDD·모듈 간 의존성 원칙은 [`docs/architecture/modular-monolith-and-modulith.md`](architecture/modular-monolith-and-modulith.md)에 정리한다.

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
