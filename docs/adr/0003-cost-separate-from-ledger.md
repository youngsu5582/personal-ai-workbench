# ADR 0003. 비용은 원장과 다른 테이블에 두고, usage 안의 폴러가 붙인다

- 상태: 제안 (2026-10-18). 구현 PR 이 머지되면 채택으로 바꾼다
- 범위: `usage` 모듈
- 앞선 결정: [ADR 0002](0002-price-book-in-db.md) — 단가표와 계산 규칙

## 결정

- 호출의 비용은 `provider_call_costs` 에 둔다. `provider_calls` 의 `cost_usd` · `cost_basis` · `price_book_version` 은 지운다.
  `provider_calls` 는 INSERT 만 받는다.
- 비용 행은 `usage` 모듈 안의 폴러가 비동기로 만든다. `ProviderCallRecorder.record()` 는 사실만 적는다.
- 역할을 셋으로 나눈다.
  - `CostPoller` (infrastructure) — 비용 행이 없는 호출을 꺼내 넘긴다
  - `ProviderCallPricingWorker` (application) — 한 건을 처리한다. 트랜잭션 경계다
  - `ModelPriceBook.price()` (domain) — 계산한다. DB 도 시계도 모르는 순수 함수다
- 비용 계산(rating)은 `usage` 모듈에 둔다. 별도 모듈로 떼지 않는다.
- Provider 가 달러로 답한 금액(`reported_amount`)은 사실이라 `provider_calls` 에 남는다.
  그것을 비용으로 옮기는 일도 폴러가 한다 (source = REPORTED).

## 문제와 사용자 영향

- 토큰 수는 Provider 가 정한 사실이고 바뀌지 않는다. 비용은 우리 단가표로 만든 해석이고, 표가 고쳐지면 다시 계산된다.
  둘을 한 행에 두면 불변인 원장에 바뀌는 칸이 생긴다.
- 비동기로 붙이면 "계산을 시도했는가" 라는 상태가 생긴다. 이것은 호출의 사실이 아니라 계산 쪽의 상태다.
  비용을 원장 칸에 두면 아직 안 본 호출과, 봤지만 단가를 못 정한 호출(맞는 표 없음 · usage 없는 실패 호출)이
  똑같이 NULL 이라 폴러가 매번 다시 집는다.
- 기록 경로는 Provider 에 돈을 쓴 **뒤에** 돈다. 여기서 예외가 나면 원장 한 줄을 잃는다
  (`TaskWorker` 는 기록 실패를 삼키고 로그만 남긴다). 기록 경로에 일을 더할수록 그 위험이 커진다.

## 제약 조건

- 운영 배포 전이라 비용 칸을 옮기는 비용이 지금 가장 작다. V3 는 main 에 있으므로 고치지 않고 새 마이그레이션에서 지운다.
- 모듈 안에서는 FK 를 쓰고, 모듈 경계를 넘을 때는 uuid 와 값만 넘긴다 (CLAUDE.md).
- 이벤트 발행 저장소(`spring-modulith-events-jpa`)는 의존성에 없다.

## 선택한 해법

```text
 generation                   │  usage
                              │
 TaskWorker ──record()──────▶ │  provider_calls          model_price_books
                              │   (사실 · INSERT 만)        (단가표)
                              │        │
                              │   CostPoller ─▶ ProviderCallPricingWorker ─▶ ModelPriceBook.price()
                              │        │
                              │        ▼
                              │  provider_call_costs  (해석)
```

스키마는 `V11__provider_call_costs.sql` 이 정본이다.

- 비용 행은 호출과 키를 공유한다. **행이 있으면 폴러가 본 호출이고**, 봤지만 정하지 못했으면 usd 가 NULL 인 행이 남는다.
- 폴러는 비용 행이 없는 호출을 id 순으로 N 건 집어 Worker 에 넘긴다.
- 선점 상태(PENDING · RUNNING)를 두지 않는다. `TaskPoller` 가 선점하는 이유는 Task 를 두 번 돌리면 돈이 두 번 나가기 때문이다.
  비용 계산은 외부 호출이 없어 두 번 돌아도 같은 값이고, 두 번째 INSERT 는 PK 에 막힌다.
- 집계(`UsageService`)는 비용 테이블을 left join 한다. 비용 행이 아직 없는 호출은 비용을 모르는 호출로 센다 → `costComplete=false`.
- Worker 의 입력은 호출 id 하나다. 부르는 쪽이 폴러에서 이벤트로 바뀌어도 Worker 와 계산은 그대로다.

이벤트를 쓰지 않는 이유 — [모듈 문서](../architecture/modular-monolith-and-modulith.md) 7절의 네 질문에 대면:

| 질문 | 기록 → 비용 |
|---|---|
| 같은 트랜잭션에서 끝나야 하나 | 아니다. 애그리거트가 달라 트랜잭션도 따로 간다 |
| 실패하면 다시 처리해야 하나 | 그렇다. 다만 "비용 행이 없음" 이 곧 재처리 목록이다 |
| 발행 순서 · 중복 소비를 관리해야 하나 | 순서는 상관없고 중복은 PK 가 막는다 |
| 호출자와 수신자를 느슨하게 묶어야 하나 | 같은 모듈 안이라 끊을 결합이 없다 |

## 선택하지 않은 대안

| 대안 | 하지 않은 이유 |
|---|---|
| `provider_calls` 의 기존 칸에 UPDATE | 시도했는지를 적을 자리가 없어 메모리 커서 같은 우회가 필요하다. 가격표를 문자열 version 으로 가리킨다 |
| `record()` 안에서 동기로 계산 | 돈을 쓴 뒤의 기록 경로에 DB 조회가 늘어난다. 옛 행을 소급하는 경로는 어차피 따로 필요하다 |
| 이벤트 (커밋 뒤 인프로세스 · Modulith outbox) | 인프로세스는 재시작에 유실되고 유실된 호출은 아무도 다시 집지 않는다. outbox 는 의존성이 늘고, 위 네 질문대로 얻는 것이 없다 |
| 별도 `cost` · `pricing` 모듈 | 주된 질문(사용자 · 모델 · 기간별 합계)이 조인 한 번으로 끝나지 않는다 — 원장 칸을 복사하거나 호출마다 부르게 된다. 계산 입력인 `usage_raw` 형식을 포트로 내보내야 한다 |

## 검증 근거

- `TaskPoller` 가 같은 구조다. Task 테이블이 outbox 이고, 폴러는 꺼내 넘기며 일은 워커가 한다.
- 비용 계산은 DB 안의 읽기와 곱셈뿐이라 같은 입력에 같은 값을 낸다. 선점 없이 PK 로 중복을 막아도 되는 근거다.

## 명시적으로 하지 않는 것

- 이미 계산한 행의 재계산. 단가는 기간으로 바뀌고 과거 기간의 표는 고치지 않는다
- 이벤트로 바로 계산하는 빠른 길
- 재계산 이력의 보관

## 다시 검토할 조건

- 한도 제어처럼 비용이 바로 보여야 하는 기능이 생긴다 → 이벤트를 빠른 길로 얹고 폴러는 안전망으로 남긴다
- 서버를 나눈다 → `generation` → `usage` 전파를 outbox + 브로커로 바꾼다. 그때 호출 uuid 는 **보내는 쪽이 정한다** —
  브로커가 다시 보내도 원장이 두 번 적히지 않게. `usage` 안의 폴러는 같은 서비스 · 같은 DB 라 그대로 둔다
- 인스턴스가 여럿이 되어 PK 충돌이 잦아진다 → `FOR UPDATE SKIP LOCKED` 로 선점
- 과거 기간의 단가를 고칠 일이 생긴다 → DERIVED 만 다시 계산하는 경로. REPORTED 는 덮지 않는다
- 환율 · 조직 귀속 · 청구가 들어온다 → billing 을 별도 모듈로 뗀다. 입력은 원문이 아니라 계산된 비용(값)이다

## 관찰할 metric과 log

- INFO log — 폴러 한 주기에 붙인 비용 행 수. 0 건인 주기는 남기지 않는다
- ERROR log — 비용 행을 만들다 터진 호출. 비용 행이 없으니 다음 주기에 다시 집힌다 — 같은 호출이 주기마다 찍히면 계속 실패하는 것이다
- SQL — 비용 행이 없는 호출 수와 그중 가장 오래된 `called_at`. 폴 주기보다 오래된 것이 남아 있으면 폴러가 멈춘 것이다
