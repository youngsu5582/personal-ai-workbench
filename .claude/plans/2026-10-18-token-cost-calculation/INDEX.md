# Plan: 단가표로 호출 비용(USD)을 계산한다
Created: 2026-10-18
Status: active

출발점: Codex 세션 `01a13fc6-9cc2-72e3-8a09-916729a6c840` (2026-10-15~16).
그 세션의 결론 — 첫 PR 은 가격표 2개 테이블 + 계산 + 기존 `provider_calls.cost_*` 채우기.
`provider_call_meters` · `provider_call_cost_lines` · 환율 · billing account 는 뒤로 미룬다.
이후 논의에서 비용을 원장과 다른 테이블로 옮기기로 바꿨다 (ADR 0003).

## Progress
- [x] 결정 확인 → ADR 0002 · 0003 (0001 은 main 의 업로드 SHA-256)
- [x] V9 · V10 — 단가표 테이블 + OpenAI 이미지 모델 단가 (V7 · V8 은 main 의 #54 · #55)
- [x] V11 — `provider_call_costs` 로 비용 분리, `ProviderCallPricingWorker`
- [x] `CostPoller`
- [x] 문서 — README 설계 결정 · roadmap 상태
- [x] PR #61 — Codex P1(마이그레이션 순서)은 번호를 main 의 다음 자리로 맞춰 닫았다
- [ ] PR 머지 뒤 — ADR 상태를 채택으로, roadmap "걸어온 길" 에 PR 추가

## 결정 (2026-10-18)
- 단가 키는 **usage_raw 경로**를 가격 데이터에 둔다 (`usage_field = 'input_tokens_details.text_tokens'`)
- 기간 겹침은 DB 로 막지 않는다 — YAGNI. 겹치면 Worker 가 고르지 않고 비용을 모름으로 둔다
- 시드 단가는 **2026-10-18 부터** 적용한다. 운영 배포 전이라 소급할 행이 없다
- 비용은 `provider_call_costs` 별도 테이블. 행이 있으면 본 호출, 못 정하면 usd NULL 행
- `CostPoller`(infra) → `ProviderCallPricingWorker`(application) → `ModelPriceBook.price()`(domain). 선점 없이 PK 로 중복을 막는다
- rating 은 usage 안. billing(환율·조직·청구)이 들어올 때 별도 모듈 후보
- 서버 분리 시 바뀌는 것은 generation → usage 전파(outbox, 호출 uuid 를 보내는 쪽이 정함). usage 안 폴러는 그대로

## 다음 후보 (이번 범위 밖)
- KRW 환산 — 환율 출처 · KST 일자 기준 · 당일 미확정 환율 표시 정책부터 정해야 한다
- 그룹(조직)별 귀속 — 호출 시점의 billing account 를 원장에 복사할지
- 실패 호출 판정 — REJECTED · THROTTLED 를 0 으로 볼지 (지금은 usd NULL 이라 costComplete=false 에 잡힌다)
- 단가표 정정 시 DERIVED 재계산 경로

## Resume Point
PR #61 리뷰 · 머지 대기. 머지되면 ADR 0002 · 0003 의 상태를 채택으로 바꾸고 roadmap "걸어온 길" 에 #61 을 더한다.
크레딧 SPEC(`docs/credit-spec` 워크트리, 커밋 전)은 보류다.

## Files
- INDEX.md — 이 파일
- docs/adr/0002-price-book-in-db.md — 단가표·계산 규칙
- docs/adr/0003-cost-separate-from-ledger.md — 비용 저장 위치·폴러·모듈 경계
