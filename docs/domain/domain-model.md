# Personal AI Workbench 도메인 모델

## 1. 문제 영역

Personal AI Workbench는 외부 AI Provider의 이미지 생성 작업을 개인 소유의 비동기 Job으로 관리한다.

핵심 흐름은 다음과 같다.

```text
생성 요청
→ GenerationJob 생성
→ Provider 작업 제출
→ 상태 동기화
→ 결과 Asset 저장
→ Job과 Asset 조회
```

이 시스템의 핵심은 Provider API를 그대로 노출하는 것이 아니라, 외부 작업을 내부 도메인 모델과 운영 가능한 상태 흐름으로 관리하는 데 있다.

## 2. Bounded Context

현재 MVP는 하나의 Bounded Context로 시작한다.

```text
AI Generation Workbench
```

외부 Provider의 모델명·상태값·응답 payload는 이 경계 안의 Provider adapter에서만 다룬다. API와 도메인 계층은 Provider별 표현을 직접 알지 않는다.

## 3. 유비쿼터스 언어

| 용어 | 의미 |
| --- | --- |
| User | Workbench의 사용자. 생성 작업과 Asset의 소유자다. |
| UserIdentity | 한 User를 증명하는 외부 인증 주체 하나. (issuer, subject)로 식별한다. |
| Issuer | 인증 발급자. OIDC면 ID token의 `iss`, 아니면 우리가 정한 상수다. |
| Subject | 그 발급자 안에서 불변인 사용자 식별자. |
| Owner | 특정 리소스를 소유하고 조회할 수 있는 User. |
| GenerationJob | 이미지 생성 요청과 처리 상태를 나타내는 내부 작업이다. |
| Provider | 실제 이미지 생성 요청을 처리하는 외부 AI 서비스다. |
| Provider Task | Provider가 발급한 외부 작업 식별자다. |
| Asset | 생성 결과 또는 입력으로 관리하는 이미지 리소스다. |
| JobAttempt | 하나의 GenerationJob을 Provider에 제출하려고 시도한 기록이다. |
| WebhookEvent | Provider가 전달한 상태 변경 이벤트의 원본과 처리 상태다. |
| Idempotency Key | 같은 요청을 중복 생성하지 않기 위한 클라이언트 제공 키다. |
| Usage Record | Provider 사용량·비용·처리 시간을 기록한 데이터다. |

## 4. 도메인 규칙과 불변식

### User / UserIdentity

- 외부 인증 주체의 식별 기준은 `provider` 같은 논리 이름이 아니라 **(issuer, subject) 쌍**이다.
- `UNIQUE(issuer, subject)` — 같은 외부 계정이 두 User에 연결될 수 없다.
- `issuer`는 저장 전에 정규화한다. Google은 `iss`를 `https://accounts.google.com`과
  `accounts.google.com` 두 형태로 줄 수 있고, 정규화하지 않으면 같은 사람이 두 행으로 갈라진다.
- `subject`는 해당 발급자 안에서 불변인 값만 쓴다. GitHub의 `login`처럼 변경 가능한 값은 쓰지 않는다.
- **로그인 수단이 0개인 User는 존재할 수 없다.** 마지막 identity는 해제할 수 없다.
  이 규칙은 `UserWriter.unlink`가 지킨다. "자식 행이 최소 1개"를 강제하는 DB 제약은 없기 때문이다.
  현재 count 확인은 TOCTOU에 열려 있다 — 해제 UI가 생기는 시점에 `@Version` 또는 비관적 락이 필요하다.
- 한 User에 같은 issuer를 두 번 연결하지 않는다 (구글 계정 두 개를 한 계정에 붙일 수 없다).
- **이메일이 같다는 이유로 기존 User에 자동 연결하지 않는다.** 공격자가 내 이메일로 다른 provider에
  계정을 만들면 내 계정에 들어오게 된다. 기존 계정에 붙이는 것은 로그인된 상태에서만 가능하다.
- `email`은 User가 아니라 identity가 주장하는 값이므로 `UserIdentity`에 둔다. 로그인 식별에는 쓰지 않는다.
- `status`가 `ACTIVE`인 User만 로그인할 수 있고 새로운 작업을 생성할 수 있다.

### Ownership

- 모든 사용자 소유 리소스는 `owner_user_id`를 가진다.
- 조회 조건에는 항상 현재 인증 주체의 User ID가 포함되어야 한다.
  단 이 규칙은 **HTTP 요청 경로에만** 적용된다. Worker 스레드에는 SecurityContext가 없으므로,
  Worker는 컨텍스트가 아니라 행에 저장된 `owner_user_id`를 신뢰한다.
- 클라이언트가 보낸 `owner_user_id`를 신뢰하지 않는다.
- 다른 User의 Job·Asset은 존재 여부가 외부에 드러나지 않도록 조회 범위에서 제외한다.

### GenerationJob

- Job 생성 요청은 즉시 최종 이미지가 아니라 `202 Accepted`와 Job 식별자를 반환한다.
- Job 상태는 정의된 전이 규칙을 벗어나 변경할 수 없다.
- Provider에 제출된 뒤에는 Provider Task ID를 저장한다.
- 이미 처리된 Idempotency Key로 새로운 작업을 만들지 않는다.
- Webhook과 Polling이 같은 이벤트를 전달해도 최종 상태가 중복 처리되지 않아야 한다.
- 최종 상태에 도달한 Job은 임의로 다시 `PENDING`이나 `RUNNING`으로 되돌리지 않는다.

## 5. Aggregate 후보

초기 구현에서는 다음 Aggregate 경계를 사용한다.

```text
User
  └─ User가 소유권의 기준이 되는 Aggregate Root

UserIdentity
  └─ User를 `user_id`로 참조하는 로그인 수단. 1:N이지만 JPA 컬렉션으로 매핑하지 않는다.
     "로그인 수단이 0개일 수 없다"는 UserWriter가 명시적으로 지킨다.

GenerationJob
  ├─ 생성 요청
  ├─ 상태
  ├─ Provider Task ID
  └─ JobAttempt / 상태 이력

Asset
  ├─ 저장 위치
  ├─ content type
  ├─ checksum
  └─ parent asset 연결
```

초기에는 모든 것을 하나의 거대한 Aggregate로 묶지 않는다. Job 처리와 Asset 저장의 일관성 요구가 다르기 때문이다.

## 6. 계층별 책임

```text
API
  └─ HTTP 입력 검증, 인증 주체 추출, 응답 변환

Application
  └─ 생성·조회·재시도 유스케이스와 트랜잭션 경계

Domain
  └─ Job 상태 전이, 소유권 규칙, 멱등성 규칙

Infrastructure
  └─ JPA Repository, PostgreSQL, Provider HTTP client, Storage

Worker
  └─ Job claim, Provider 호출, polling, webhook event 처리
```

Controller가 Provider API를 직접 호출하지 않는다. MCP도 Controller가 아니라 Application Service를 사용한다.

## 7. 구현 원칙

- 도메인 규칙은 주석만으로 끝내지 않고 테스트로 검증한다.
- Provider별 상태값은 adapter에서 내부 Job 상태로 변환한다.
- 처음부터 다중 Provider 플랫폼을 만들지 않고 T2I 한 흐름을 완성한다.
- 도메인 모델을 실제 사용하면서 발견한 규칙은 이 문서와 테스트에 함께 반영한다.

## 8. 현재 범위와 다음 범위

현재 구현:

- `User` / `UserIdentity` (1:N, `UNIQUE(issuer, subject)`)
- `UserRegistry` — user 모듈의 공개 경계
- Google OIDC 로그인, JIT provisioning, 이메일 allowlist
- 자체 access token 발급(HS256)과 Bearer 검증
- `AuthenticatedPrincipal` / `OwnerContext`
- 로컬 PostgreSQL 개발 환경, H2 기반 테스트 환경

다음 구현:

1. refresh token (rotation + reuse detection)
2. GitHub provider — OIDC가 아니므로 issuer를 상수로 정한다
3. 계정 연결 (로그인 상태에서 두 번째 provider 연결)
4. `GenerationJob` 모델
5. Owner scope 조회와 Job 생성 API
