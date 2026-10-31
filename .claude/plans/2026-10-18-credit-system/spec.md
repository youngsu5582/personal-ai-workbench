# SPEC: 호출자별 크레딧 — 정해 둔 요청만, 정해 둔 만큼

Created: 2026-10-18
Status: 확정 — 결정은 `docs/adr/0004-credit-grants-and-ledger.md` 가 정본이다. 이 문서는 구현의 작업 지시다
코드와 대조: 2026-10-18 (main `2703dfc` — #55 의 sha256 식별자, #60 의 Provider 지표, #61 의 비용 계산, #63 까지)

## 무엇을 만드나

호출자(내 서비스 · 사람)가 **어떤 모델을 얼마나** 쓸 수 있는지 접수 시점에 정하고 지킨다.
크레딧은 여러 길(일간 · 월간 지급, 운영자 지급, 나중에는 충전)로 들어오고, 쓸 때는 정해진 순서로 빠진다.

**완료 기준(1문장)**: 크레딧 단가가 정해진 요청만 접수되고, 접수할 때 그 요청의 크레딧이 호출자의 묶음들에서
소비 순서대로 빠지며, 모자라면 Provider 를 부르기 전에 거절되고, 최종 실패한 Task 의 크레딧은 꺼낸 묶음으로 돌아간다.

### 비용(cost)과 다른 것

| | 비용 — ADR 0002 · 0003 | 크레딧 — 이 SPEC |
|---|---|---|
| 답하는 질문 | 우리가 Provider 에 **얼마를 냈나** | 이 호출자가 **무엇을 얼마나 더 쓸 수 있나** |
| 정해지는 때 | 호출 **뒤** (usage 가 와야 계산되고 폴러가 붙인다) | 호출 **전** (접수할 때 막아야 한다) |
| 단위 | USD, 소수 8자리 | 정수 크레딧 |
| 누가 정하나 | Provider 의 사용량 × 공식 단가 | 우리 정책 |

그래서 **크레딧을 비용에서 파생하지 않는다.** 둘의 차이(타임아웃을 우리가 떠안은 금액, 단가 설정의 오차)는 비용 데이터로 따로 본다.

### 막으려는 일

- 한 호출자의 버그 · 반복 호출이 Provider 예산 전체를 태운다
- 호출자가 쓰지 않기로 한 모델 · 품질을 쓴다
- 결과를 못 받은 요청에 크레딧이 빠진 채 남는다
- 동시에 들어온 요청 둘이 남은 크레딧을 함께 통과한다

## 확인된 사실

코드에서 — `feat/cost-calculate` 기준.

| # | 사실 | 무엇을 정했나 |
|---|---|---|
| F1 | 접수는 `GenerationJobService.submit` → (트랜잭션 밖) 모델 · 입력 소유 확인 → `GenerationJobWriter.create` (트랜잭션) Job · Task 기록 순이다. KDoc 이 "크레딧 차감은 기록과 같은 트랜잭션" 을 이미 예고한다 | 차감 자리는 `create` 의 트랜잭션 안이다 (D3) |
| F2 | Task 하나 = Provider 호출 한 번 = 결과물 한 장. `taskCount` 는 1 ~ `MAX_TASK_COUNT`(4) | 차감 단위는 Task 다 (D4) |
| F3 | 재시도는 `retryable && attemptCount < MAX_ATTEMPTS(3)` 일 때 FAILED → PENDING 이다. 같은 Task 가 최대 3번 호출된다 | 재시도는 크레딧을 다시 빼지 않는다 (D4) |
| F4 | 최종 실패는 `TaskStateWriter.fail` 의 재시도하지 않는 분기, 성공은 `succeed` 다. 둘 다 짧은 트랜잭션이다 | 환불 자리는 `fail` 의 그 분기다 (D12) |
| F5 | RUNNING 고착을 회수하는 기능이 없다 (`TaskStateWriter.succeed` KDoc). 워커가 죽으면 Task 가 RUNNING 에 남는다 | 그 Task 의 크레딧은 묶인 채 남는다 — 함정, 작업 순서 4 |
| F6 | T2I · I2I 모두 `quality` 가 있고 기본값이 `AUTO` 다. AUTO 는 Provider 가 품질을 정한다 | AUTO 에도 단가를 둔다 (D16) |
| F7 | gpt-image-2 1024² 장당 low $0.006 · high $0.211 (가이드, 10-18) — 품질이 비용을 약 35배 가른다 | 단가 키에 품질이 들어간다 (D16) |
| F8 | 관리자 개념이 없다. 접근 통제는 `AUTH_ALLOWED_EMAILS` 뿐이다 | 호출자별 정책 · 운영 지급은 처음에 SQL 로 한다 (작업 순서 5) |
| F9 | 호출자는 `owner_user_uuid` 다. 서비스용 OAuth Server 는 아직이다 | 크레딧의 키는 `owner_user_uuid` 다 |
| F10 | `/api/usage` 의 "이번 달" 은 **UTC** 월초부터다 (`UsageController.startOfMonth`) | 일 · 월 경계도 UTC 다 (D15) |
| F11 | 접수 거절의 응답 매핑은 `GenerationApiExceptionHandler` 가 컨트롤러 단위로 한다 | 크레딧 거절 응답도 거기서 정한다 (D17) |

## 큰 그림 — 테이블 다섯 개

| | 테이블 | 한 행의 뜻 | 바뀌나 | 누가 쓰나 |
|---|---|---|---|---|
| 정책 | `credit_prices` | 이 모델 · 품질은 장당 N 크레딧 (기간) | 새 기간 행만 더한다 | 마이그레이션 |
| 정책 | `credit_grant_policies` | 누구에게(NULL = 모두) 어떤 주기로 얼마를 준다 (기간) | 새 기간 행만 더한다 | 마이그레이션 · 운영 SQL |
| 사실 | `credit_grants` | **들어온 묶음** 하나 | `remaining` 만 (캐시) | 지급 |
| 사실 | `credit_ledger_entries` | **움직인 사실** 하나 — Task 별 CHARGE · REFUND | 안 바뀐다 | 차감 · 환불 |
| 사실 | `credit_allocations` | 그 움직임이 **어느 묶음에서 얼마였나** | 안 바뀐다 | 차감 · 환불 |

```text
 정책                                   사실
 credit_grant_policies ──(지급할 때)──────────────────────────────────────────▶ credit_grants
                                                                                  ▲  들어온 것
 credit_prices ──(차감할 때 단가)──▶ credit_ledger_entries ──1:N──▶ credit_allocations ──N:1┘
                                     움직인 것 (Task 단위)          어느 묶음에서 얼마
```

**묶음은 들어온 기록, 원장은 움직인 기록, 할당은 둘을 잇는 기록이다.**
잔액은 어디에도 저장하지 않는다 — 살아 있는 묶음의 `remaining` 합이다.

## 동작

### ① 지급

일간 · 월간은 **게으르게** 만든다. 차감이나 조회가 그 호출자를 처음 볼 때, 이번 기간에 적용되는 정책마다:

```sql
INSERT INTO credit_grants (owner_user_uuid, source, ref, amount, remaining, granted_at, expires_at, policy_id)
VALUES (:owner, 'MONTHLY', '2026-10', 1000, 1000, now(), '2026-11-01T00:00:00Z', :policy)
ON CONFLICT (owner_user_uuid, source, ref) DO NOTHING
```

- 적용 정책은 출처마다 **호출자 행이 있으면 그것, 없으면 기본(owner NULL)** 이다. 호출자 행의 `amount = 0` 은 "이 출처는 안 준다" 다
- `ref` 는 DAILY `YYYY-MM-DD`, MONTHLY `YYYY-MM`. 만료는 다음 UTC 일 · 월의 시작이다
- ADMIN 은 운영 SQL 이 직접 넣는다. `ref` 는 운영 메모의 식별자, 만료는 넣는 사람이 정한다(없어도 된다)
- PURCHASE 는 결제가 생기면 그 흐름이 넣는다. `ref` 는 결제 id — 같은 결제가 두 번 지급되지 않는다

### ② 견적

`credit_prices` 에서 `(model, quality)` 와 시각으로 장당 크레딧을 찾는다. 없으면 그 요청은 받지 않는다(400).
`submit` 의 트랜잭션 밖에서 한 번 — 이르게 거절하려고 — 보고, 차감할 때 트랜잭션 안에서 **다시** 정한다.
밖에서 읽은 값을 넘기면 그 사이 단가표의 경계가 지나갈 수 있다.

### ③ 차감 — `GenerationJobWriter.create` 의 트랜잭션 안

```text
1. ① 의 게으른 지급
2. SELECT … FROM credit_grants
    WHERE owner_user_uuid = :owner AND remaining > 0 AND (expires_at IS NULL OR expires_at > now())
    ORDER BY expires_at ASC NULLS LAST, (source = 'PURCHASE') ASC, granted_at ASC, id ASC
    FOR UPDATE
3. Task 마다 장당 단가만큼, 위 순서대로 꺼낸다. 모자라면 예외 → Job 까지 전부 롤백 → 402
4. Task 마다 원장 CHARGE 한 줄(−단가, credit_price_id) + 할당 CHARGED 1~2줄, 묶음의 remaining 을 줄인다
```

### ④ 환불 — `TaskStateWriter.fail` 의 재시도하지 않는 분기, 같은 트랜잭션

```text
1. 원장 REFUND 한 줄 — 실제로 돌아간 양. 0 이어도 쓴다. 이 행이 "그 Task 의 환불은 처리됐다" 의 표시다
   (UNIQUE (kind, task_uuid) 에 걸리면 이미 처리된 것이니 아무것도 하지 않는다)
2. 그 Task 의 CHARGED 할당을 묶음별로 되돌린다
     묶음이 살아 있으면  → 할당 RETURNED, remaining 을 되돌린다
     묶음이 만료됐으면   → 할당 FORFEITED, 묶음은 건드리지 않는다
```

환불은 **"그 요청이 없었던 상태로 되돌리기"** 다. 요청이 없었어도 만료된 묶음의 크레딧은 이미 사라졌을 것이므로 돌려주지 않는다.
돌려주지 않은 사실은 FORFEITED 로 남는다 — 남기지 않으면 "처리했나", "정책인가 버그인가", "얼마나 자주인가" 를 판단할 수 없다.

### ⑤ 만료

**아무 작업도 하지 않는다.** 잔액을 셀 때 `expires_at` 이 지난 묶음이 빠질 뿐이다.
만료 때 안 쓰고 남은 양은 그 묶음의 `remaining`, 환불 때 못 돌려준 양은 FORFEITED 합이다.

### 예 — 자정을 걸친 요청

```text
credit_grant_policies   #1  owner NULL · DAILY 50     #2  owner NULL · MONTHLY 1000
credit_prices           #7  gpt-image-2 · high · 22

10-18 23:59  high 1장 접수 → D18 에서 10, M10 에서 12 (소비 순서)
10-19 00:03  세 번째 시도도 실패 → 최종 실패 → 환불

credit_grants           D18  DAILY   2026-10-18    50  remaining 0     만료 10-19 00:00  ← 만료
                        D19  DAILY   2026-10-19    50  remaining 50    만료 10-20 00:00
                        M10  MONTHLY 2026-10     1000  remaining 1000  만료 11-01 00:00
credit_ledger_entries   #31  CHARGE  t1  −22  price #7
                        #47  REFUND  t1  +12
credit_allocations      #31 → D18  10  CHARGED     #31 → M10  12  CHARGED
                        #47 → M10  12  RETURNED    #47 → D18  10  FORFEITED

잔액 = D19 50 + M10 1000 = 1050   (요청이 없었을 때와 같다)
```

## 결정 사항

결정의 이유와 대안은 ADR 0004 에 있다. 여기는 구현이 따라야 할 목록이다.

| # | 결정 |
|---|---|
| D1 | 크레딧은 비용에서 파생하지 않는다. 우리가 정한 **요청 단위 가격**이다 |
| D2 | `credit` 모듈을 새로 둔다. generation 은 루트 포트에 **값만** 넘긴다. credit 은 generation · usage 를 모른다 |
| D3 | 차감은 Job 기록과 **같은 트랜잭션**이다 |
| D4 | 차감 단위는 **Task** 다. 접수 때 Task 마다 CHARGE 한 줄, 재시도는 다시 빼지 않는다 |
| D5 | 크레딧은 **묶음**으로 들어온다. 출처는 DAILY · MONTHLY · ADMIN · PURCHASE 이고, 기간 한도는 "만료가 있는 묶음" 이다 |
| D6 | **지급 정책은 묶음과 다른 테이블**이다. owner NULL 이 기본, 호출자 행이 출처마다 기본을 덮는다. 정책을 바꿔도 이미 만든 묶음은 그대로다 |
| D7 | 일간 · 월간 지급은 **게으르게** 만든다. 스케줄러가 없고 `UNIQUE (owner_user_uuid, source, ref)` 가 두 번 지급을 막는다 |
| D8 | 소비 순서는 **만료 임박 → 무료 → 유료 → 오래된 순**이다 |
| D9 | 동시성은 호출자의 살아 있는 묶음을 **소비 순서대로 `FOR UPDATE`** 잠가 지킨다. 모든 트랜잭션이 같은 순서로 잠가 교착이 없다 |
| D10 | 원장은 **움직임만** 적는다(CHARGE · REFUND). 지급은 묶음이 기록이다. append-only, `UNIQUE (kind, task_uuid)` |
| D11 | 할당은 **별도 테이블**이다. FK · CHECK 가 걸리고, 묶음 기준 쿼리(검산 · 이력 · FORFEITED 합)가 생긴다 |
| D12 | 환불은 그 Task 의 할당을 되돌린다. 살아 있는 묶음 몫은 RETURNED, 만료된 묶음 몫은 FORFEITED. 0 이어도 REFUND 행을 쓴다 |
| D13 | 최종 실패한 Task 는 **사유와 상관없이** 환불 대상이다. 타임아웃으로 우리가 과금됐을 수 있어도 그 손실은 비용 데이터가 보여준다 |
| D14 | 잔액은 저장하지 않는다. `remaining` 은 할당으로 다시 셀 수 있는 캐시다 — `remaining = amount − ΣCHARGED + ΣRETURNED` |
| D15 | 일 · 월 경계는 **UTC** 다 |
| D16 | 단가는 **전역 표**, 키는 **모델 + 품질**, `AUTO` 에도 따로 둔다(기본은 HIGH 와 같게). 정수, 기간, 마이그레이션으로만 바뀐다 |
| D17 | 크레딧 부족은 **402** + `required` · `available` · `nextGrantAt`. 단가 없는 요청은 **400** |
| D18 | 크레딧은 정수이고 USD 와 고정 환율이 없다. 단가를 정할 때의 기준으로만 "1 크레딧 ≈ 1센트" 를 쓴다 |
| D19 | 사용 상한(cap)은 범위 밖이다. 잔액과 **별개의 가드**로 나중에 더한다 |

## 데이터

```text
credit_prices
  id · model varchar(255) · quality varchar(32) · credits_per_image int (> 0)
  effective_from timestamptz · effective_to timestamptz NULL · note
  UNIQUE (model, quality, effective_from)
  -- quality 는 generation 의 enum 이 아니라 표기 문자열('low' · 'high' · 'auto')이다. credit 은 generation 을 모른다

credit_grant_policies
  id · owner_user_uuid uuid NULL · source varchar(32) ∈ (DAILY, MONTHLY) · amount int (>= 0)
  effective_from timestamptz · effective_to timestamptz NULL · note

credit_grants
  id · owner_user_uuid uuid · source varchar(32) ∈ (DAILY, MONTHLY, ADMIN, PURCHASE) · ref varchar(64)
  amount int (> 0) · remaining int (0 <= remaining <= amount)
  granted_at timestamptz · expires_at timestamptz NULL · policy_id → credit_grant_policies NULL
  UNIQUE (owner_user_uuid, source, ref)
  INDEX (owner_user_uuid, expires_at)

credit_ledger_entries
  id · owner_user_uuid uuid · kind varchar(32) ∈ (CHARGE, REFUND)
  task_uuid uuid · job_uuid uuid · credits int (부호 있음) · credit_price_id → credit_prices NULL · created_at timestamptz
  UNIQUE (kind, task_uuid)

credit_allocations
  id · ledger_entry_id → credit_ledger_entries · grant_id → credit_grants
  credits int (> 0) · outcome varchar(32) ∈ (CHARGED, RETURNED, FORFEITED)
  INDEX (grant_id)
```

검산 — 셋이 늘 성립해야 한다. 깨지면 할당이 정본이다.

- 묶음마다 `remaining = amount − ΣCHARGED + ΣRETURNED`
- CHARGE 행마다 `credits = −Σ 그 행의 CHARGED`, REFUND 행마다 `credits = +Σ 그 행의 RETURNED`
- Task 마다 `Σ CHARGED = Σ RETURNED + Σ FORFEITED` (환불된 Task 에 한해)

## 모듈 경계

```text
generation ── credit 루트 포트 (값만) ──▶ credit
  submit  : quote(model, quality)                              → 장당 크레딧 | null   (트랜잭션 밖, 읽기)
  create  : charge(owner, jobUuid, taskUuids, model, quality)  → 실패면 예외          (generation 트랜잭션에 참여)
  fail    : refund(taskUuid)                                   → 멱등                (generation 트랜잭션에 참여)
```

- 포트가 generation 의 트랜잭션에 **참여한다**는 것이 계약의 일부다. 서버를 나누면 이 줄이 가장 먼저 깨진다 — 그때는 예약 → 확정(사가)으로 바꾼다
- 부족 · 단가 없음은 credit 루트의 예외 타입으로 넘어오고, 응답은 generation 의 `GenerationApiExceptionHandler` 가 정한다 (F11)
- 이름은 구현할 때 `conventions.json` 에 맞춰 정한다. 규칙에 없는 역할이 나오면 그때 묻는다

## API

- `POST /api/jobs` — 부족이면 402 `{ required, available, nextGrantAt }`, 단가 없음이면 400
- `GET /api/credits` — 내 잔액, 살아 있는 묶음 목록(출처 · amount · remaining · expiresAt), 다음 지급 시각
  - 조회도 ① 의 게으른 지급을 **별도 트랜잭션으로** 부른다. 멱등이라 GET 이 쓰는 것이 결과를 바꾸지 않고, 지급 규칙이 한 곳에만 있다

## 수용 기준 — 테스트로 고정할 것

지급
- 첫 요청에서 이번 기간의 DAILY · MONTHLY 묶음이 생기고, 두 번째 요청에서는 다시 생기지 않는다
- 처음 오는 두 요청이 동시에 와도 묶음은 출처마다 하나씩만 생긴다
- 호출자 정책 행이 출처마다 기본 정책을 덮는다. `amount = 0` 이면 그 출처의 묶음은 없다
- 정책을 바꿔도 이미 만든 묶음은 그대로이고 다음 기간부터 바뀐다

차감
- 단가가 없는 모델 · 품질은 400 이고, Job · 원장 · 할당이 생기지 않는다
- `AUTO` 요청도 접수 시점에 단가가 정해진다
- 차감은 소비 순서대로 쪼개진다 — DAILY 10 이 남았을 때 22 를 빼면 DAILY 10 + MONTHLY 12
- 모자라면 402 이고, Job · 원장 · 할당 · `remaining` 이 그대로다
- 남은 양으로 하나만 들어갈 때 두 요청이 동시에 오면 하나만 성공한다
- 정책이 하나도 없는 호출자는 묶음이 없어 402 다

환불
- 재시도 중의 실패는 환불하지 않고 최종 실패한 Task 만 환불한다
- 접수 뒤 단가표가 바뀌어도 환불은 접수 때 꺼낸 양이다
- 살아 있는 묶음 몫은 RETURNED 로 잔액에 돌아오고, 만료된 묶음 몫은 FORFEITED 로 남고 잔액에 돌아오지 않는다
- 전부 만료된 몫이어도 REFUND 행(0)이 하나 생기고, 같은 Task 로 다시 불러도 아무것도 바뀌지 않는다

검산
- 위 시나리오를 모두 돈 뒤 "데이터" 절의 검산 셋이 성립한다

## 범위 밖

- PURCHASE 의 결제 흐름 — 묶음 출처와 `ref` 자리만 둔다
- 사용 상한(cap) (D19), 운영 차감(ADJUST)
- 호출자별 단가 — 필요해지면 `credit_prices.owner_user_uuid` 를 nullable 로 더한다. 기존 행이 그대로 전역이 되는 안전한 변경이다
- 그룹(조직)별 크레딧 — billing account 가 생긴 뒤
- 크기별 단가, 견적 API
- 크레딧 소비와 실제 비용(USD)의 대사 화면 — 운영 콘솔

## 함정

- **RUNNING 에 남은 Task 의 크레딧은 묶인 채 남는다** (F5). 회수 기능이 Task 를 FAILED 로 닫을 때 같은 트랜잭션에서 `refund` 를 불러야 한다. PENDING 으로 되돌리면 환불하지 않는다 — 다시 돈다
- **차감을 `submit` 에서 하면 안 된다.** `submit` 이 트랜잭션을 두지 않는 이유는 입력 확인이 보관소를 타서다 (F1). 차감은 `create` 안이어야 한다
- **환불 금액을 지금 단가표로 다시 계산하면 안 된다.** 할당이 정본이다
- **잠그는 순서가 트랜잭션마다 달라지면 교착이 생긴다.** 잠금 순서 = 소비 순서이고, 끝에 `id` 를 붙여 순서를 완전히 정한다
- **부족으로 롤백된 트랜잭션의 게으른 지급도 함께 사라진다.** 다음 요청이 다시 만든다 — 정상이다. 조회 API 가 지급을 별도 트랜잭션으로 부르는 이유이기도 하다
- **일간 경계는 UTC 자정, 한국 시간 오전 9시다.** 환불 FORFEITED 가 생기는 시각이다
- **429 를 쓰지 않는다.** 클라이언트가 "잠깐 뒤 재시도" 로 읽으면 버그 난 호출자가 부족 응답에 재시도를 퍼붓는다

## 작업 순서

| 순서 | PR | 무엇 | 혼자 머지돼도 되는 이유 |
|---|---|---|---|
| 0 | `docs/credit-spec` | 이 SPEC 과 ADR 0004 | 문서뿐이다. `feat/cost-calculate` 위에 쌓였다 |
| 1 | 단가표와 거절 | `credit` 모듈 뼈대, `credit_prices` + 지금 모델들의 시드, `quote`, `submit` 에서 단가 없는 요청 400 | "정해 둔 요청만" 이 먼저 선다. 차감이 없어 아무도 막히지 않는다 — 시드에 지금 쓰는 모델이 모두 있어야 한다 |
| 2 | 지급과 조회 | `credit_grant_policies` · `credit_grants`, 게으른 지급, `GET /api/credits`, 기본 정책 시드 | 잔액이 보이기만 하고 빠지지 않는다. 정책 값을 실제로 보고 정할 수 있다 |
| 3 | **차감과 환불** | 원장 · 할당, `create` 안의 차감, `fail` 안의 환불, 402 | **둘을 한 PR 로 한다.** 차감만 머지되면 실패한 요청의 크레딧이 돌아오지 않는 상태가 main 에 생긴다 |
| 4 | RUNNING 고착 회수 (generation) | 오래된 RUNNING 을 FAILED 로 닫고 같은 트랜잭션에서 환불 | 3 이 들어가면 고착된 Task 가 크레딧을 붙잡는다. 크레딧을 운영에 켜기 전에 있어야 한다 |
| 5 | 운영 절차 | 호출자별 정책 · ADMIN 지급 SQL, 검산 쿼리를 `docs/development.md` 에 | 코드 없이 문서와 SQL 이다 |

3 이 가장 위험하다(동시성 · 원자성). 1 · 2 가 먼저 들어가 있으면 3 은 "빼고 돌려주기" 에만 집중할 수 있다.
