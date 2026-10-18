# ADR 0002. 호출 비용은 DB 의 단가표로 계산한다

- 상태: 채택 (2026-10-18, #61 머지)
- 범위: `usage` 모듈
- 이어지는 결정: [ADR 0003](0003-cost-separate-from-ledger.md) — 계산한 비용을 어디에 두고 언제 붙이나

## 결정

Provider 호출의 USD 비용을 DB 의 단가표(`model_price_books`)로 계산한다.

- 단가표 한 벌은 provider · model · 적용 기간 `[effective_from, effective_to)` 로 정해진다. `effective_to` 가 NULL 이면 지금 적용 중이다.
- 단가는 **`usage_raw` 안의 경로**에 매긴다. 표의 `rates`(jsonb)가 "경로 → 1M 당 USD" 다 — `{"usdPerMillion": {"output_tokens": 30, …}}`.
- 단가표는 관리 API 없이 Flyway 마이그레이션으로만 넣고 바꾼다. 적용된 표의 단가는 고치지 않는다 —
  단가가 바뀌면 기존 표의 `effective_to` 를 닫고 새 표를 더한다.
- 비용 = Σ(경로의 값 × 단가 ÷ 1,000,000). 계산할 수 없으면 0 이 아니라 모른다(NULL) 이다.

## 문제와 사용자 영향

- `/api/usage` 는 비용을 모르는 호출 수를 함께 내지만(`costComplete`), 단가표가 없어 모든 호출의 비용이 비어 있다.
  "이번 달 얼마 썼나" 에 답하지 못한다.
- 단가는 시간에 따라 바뀐다 — 가격 인하, 기간 한정 할인. 지금 단가를 과거 호출에 곱하면 과거 지출이 틀린다.
- 사용자별(나중에는 그룹별) 기간 합계를 조회할 수 있어야 한다.

## 제약 조건

- 원장 `provider_calls` 는 Provider 가 준 사용량을 원문(`usage_raw`)으로만 갖는다. 필드명은 Provider 의 것이다.
- `usage` 모듈은 `generation` · `provider` 모듈을 모른다 (`ProviderCallRecorder` KDoc).
- OpenAI 가격표는 단가의 적용 시작일을 밝히지 않는다.
- 우리는 OpenAI Images API 를 직접 부른다. 캐시 입력 단가는 Responses API 에만 적용되므로 해당하지 않는다.
- 운영 배포 전이라 소급할 운영 데이터가 없다.

## 선택한 해법

스키마는 `V9__model_price_books.sql`, 첫 단가는 `V10__openai_image_prices.sql` 이 정본이다. 여기에는 모양을 정한 이유만 적는다.

- **모델마다 표가 경로를 고른다.** 텍스트 출력 단가가 없는 gpt-image-2 의 표는 `output_tokens` 전체에 이미지 출력 단가를 매긴다.
  텍스트 출력 단가가 있는 모델(gpt-image-1.5)을 들이면 그 표는 `output_tokens_details.image_tokens` 와
  `output_tokens_details.text_tokens` 에 따로 매긴다. 코드에 모델별 분기가 생기지 않는다.
- **장당 가격이 아니라 토큰 단가를 둔다.** gpt-image-2 · gpt-image-2.5-sunburst · gpt-image-2.5-flare 는 토큰 단가가 같지만,
  같은 품질 · 크기에서도 쓰는 토큰 수가 달라 장당 비용은 다르다. 응답의 실제 토큰 수를 곱하므로 그 차이가 그대로 반영된다.
- **첫 단가는 2026-10-18 부터 적용한다.** 가격표가 시작일을 밝히지 않으므로 단가를 정한 날부터다. 그 전의 호출은 비용을 모르는 채로 둔다.
- **단가는 표의 jsonb 칸 하나에 둔다.** 표 한 장이 늘 통째로 읽히고, 그 안을 SQL 로 묻지 않으며, 경로의 종류가 모델마다 다르다.
  마이그레이션에서도 한 줄이 한 모델의 표 한 장이 되어, 단가가 바뀌는 PR 이 그 줄 하나로 읽힌다.
- **단가 맵은 값 객체(`PriceRates`)로 감싼다.** 표 전체에 걸리는 칸(통화 등)이 생기면 같은 객체에 더해 옛 행을 그대로 읽는다.
  jsonb 안의 값에는 DB 제약을 걸지 않는 대신 빈 표 · 음수 단가를 그 생성자가 막고, DB 에서 읽을 때도 생성자를 거친다.
  계산(경로별 곱셈)도 이 객체가 갖는다.

계산 규칙:

- `effective_from <= called_at < effective_to` 인 표를 고른다. 경계 시각의 호출은 새 표가 받는다.
- 표가 가리키는 경로 중 하나라도 `usage_raw` 에 없으면 그 호출은 계산하지 않는다. 부분 합계를 비용으로 적지 않는다.
- 호출 단위에서 센트로 반올림하지 않는다. 저장 눈금(소수 8자리)에서만 맞춘다.

## 선택하지 않은 대안

| 대안 | 하지 않은 이유 |
|---|---|
| 단가를 코드(enum · 설정 파일)에 둔다 | 기간별 이력을 조회하거나 조인할 수 없다 |
| 가격 관리 API | 가격 변경이 드물다. 누가 어떤 근거로 바꿨는지는 마이그레이션 PR 에 남는 편이 낫다 |
| 운영 DB 에 직접 INSERT | 누가 왜 바꿨는지 남지 않는다 |
| 공통 meter enum(`TEXT_INPUT_TOKENS` …) + Provider 별 추출 코드 | `usage` 가 OpenAI 필드명을 알거나 `provider` 모듈에 의존하게 된다. 경로를 데이터에 두면 Provider 추가가 가격 행 추가로 끝난다 |
| 모델 · 품질 · 크기별 장당 가격 | 조합마다 표가 갈라지고, 같은 설정에서도 실제 토큰 수가 달라 청구와 어긋난다 |
| 단가를 행으로 펼친다(`model_price_rates`) | 마이그레이션이 단가 종류별 INSERT 로 흩어져 한 모델의 표를 한눈에 볼 수 없다. 그 안을 SQL 로 묻지 않아, 행이 주는 것(조인 · 행 단위 제약)을 쓸 일이 없다 |
| 평평한 맵(`{"output_tokens": 30}`) | 표 전체에 걸리는 칸을 더할 자리가 없어, 생기는 순간 저장 모양을 바꿔야 한다 |
| 단가마다 객체(`{"output_tokens": {"usdPerMillion": 30}}`) | 단가 하나하나에 붙을 정보(구간 등)는 아직 없다. 지금 두면 없는 요구를 짐작해 모양을 정하게 된다 |
| `effective_from` 만 두고 가장 최근 표를 고른다 | 할인이 언제 끝나는지, 미래에 예약된 단가가 무엇인지 표에 드러나지 않는다 |
| 정규화 사용량 테이블(`provider_call_meters`) | 지금은 그 테이블로 답할 질문이 없다. 원문이 남아 있어 필요해지면 만들 수 있다 |
| 산식 라인 테이블(`provider_call_cost_lines`) | 표가 불변이라 어느 표로 계산했는지만 남기면 산식을 재현할 수 있다 |
| 금액 칸을 `numeric(24, 12)` 로 넓힌다 | 소수 8자리에서 호출당 반올림 오차는 5e-9 달러 이하다 |

## 검증 근거

- [OpenAI API Pricing](https://developers.openai.com/api/docs/pricing) — 단가는 토큰 종류(텍스트 입력 · 이미지 입력 · 이미지 출력)별 1M 당 USD 다.
  GPT Image 2 · 2.5 의 캐시 입력 단가는 Responses API 에만 적용된다.
- [Image generation guide](https://developers.openai.com/api/docs/guides/image-generation) —
  비용은 입력 텍스트 토큰 + 입력 이미지 토큰(edits) + 이미지 출력 토큰의 합이다. Images API 직접 요청에는 캐시 단가가 적용되지 않는다.
  토큰 단가가 같아도 장당 비용은 모델 · 품질에 따라 다르다.
- [Images API reference](https://developers.openai.com/api/reference/resources/images) — usage 는
  `input_tokens` · `input_tokens_details.{text_tokens, image_tokens}` · `output_tokens` · `total_tokens` ·
  `output_tokens_details.{image_tokens, text_tokens}` 다. 경로 키는 이 이름들을 그대로 쓴다.

## 명시적으로 하지 않는 것

- KRW 환산과 환율 이력
- 그룹(조직)별 귀속과 조회
- 실패한 호출의 비용 판정 — REJECTED · THROTTLED 를 0 으로 볼지
- 요청 파라미터로 추정하는 ESTIMATED 계산 — 토큰을 주지 않는 Provider 용
- 같은 모델의 기간 겹침을 DB 제약으로 막는 것 — 마이그레이션 리뷰가 본다
- 2026-10-18 이전 호출의 소급

## 다시 검토할 조건

- 가격 변경이 잦아지거나 계약별 단가가 생긴다 → 관리 API
- 겹친 기간이 한 번이라도 들어간다 → `EXCLUDE` 제약 (`btree_gist`)
- 토큰이 아니라 크기 · 품질 · 길이로 과금하는 Provider 를 들인다 → 경로 키로 표현되는지 다시 본다
- 단가가 숫자 하나로 표현되지 않는다(구간 단가 등) → `rates` 값의 모양. 단가표는 작고 마이그레이션으로만 들어오므로 옛 표를 함께 옮긴다
- Provider 를 가로질러 토큰 항목별로 분석할 일이 생긴다 → `provider_call_meters`
- Provider 청구서와 대사할 일이 생긴다 → `provider_call_cost_lines`

## 관찰할 metric과 log

- WARN log — 비용을 정하지 못한 호출. 호출 uuid · model · 이유(사용량 없음 / 맞는 표 없음 / 표 겹침 / 표가 가리키는 칸 없음)
- `/api/usage` 의 `costKnownCalls / calls` — 비용을 아는 호출의 비율
- 새 모델의 표를 넣었는데 그 모델의 호출이 계속 비용을 모르면, 경로나 모델명이 틀린 것이다
