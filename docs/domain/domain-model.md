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

### User

- `externalSubject`는 외부 인증 주체와 연결되는 값이며 중복될 수 없다.
- `status`가 `ACTIVE`인 User만 새로운 작업을 생성할 수 있다.
- 로컬 개발에서는 `local:default` Subject를 가진 `default-user`를 사용한다.

### Ownership

- 모든 사용자 소유 리소스는 `owner_user_id`를 가진다.
- 조회 조건에는 항상 현재 인증 주체의 User ID가 포함되어야 한다.
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

- `User`
- `UserRepository`
- `AuthenticatedPrincipal`
- `OwnerContext`
- 로컬 PostgreSQL 개발 환경
- H2 기반 테스트 환경

다음 구현:

1. `default-user` seed
2. `GenerationJob` 모델
3. Owner scope 조회
4. Job 생성 API
5. 상태 전이 테스트
