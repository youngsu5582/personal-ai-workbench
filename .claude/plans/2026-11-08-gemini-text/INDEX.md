# Plan: Gemini 텍스트 모델로 text-generate 를 받는다
Created: 2026-11-08
Status: active

## Progress
- [x] 모델 ID · Free Tier · 단가 · API 모양 확인 (공식 문서)
- [x] SPEC · D1~D3 결정 (SPEC §2)
- [x] effort `minimal` + 어댑터가 표현할 수 있는 effort 를 접수에서 400
- [x] `PriceRates.zeroIfAbsent` + ADR 0002 하위 절
- [x] provider/gemini 어댑터 · 설정 키 · 단위 · live 테스트
- [x] V19 단가표 (main 의 V18 템플릿과 겹쳐 번호를 옮겼다) (3.6-flash 는 2027-01-01 전후 두 장) + PriceBookSeedTest
- [x] development.md · scripts/text.sh
- [x] main(#101) 위로 rebase 후 전체 테스트 530개 통과 (2026-11-10). 템플릿 저장도 `requireSupported` 를 거쳐 effort 를 본다
- [x] 로컬 스모크 (2026-11-10, 임시 DB · OpenAI 끔) — `/api/models` 에 두 모델, 형식 없는 글 · JSON Schema 접수와 결과,
  thinking 칸이 없는 호출의 비용이 손 계산과 같다, `xhigh` 는 접수 · 템플릿 저장 모두 400, `minimal` 템플릿 실행 성공
- [x] live 확인 (2026-11-08, 두 모델 × 4건 통과) — 대문자 `thinkingLevel` 을 받는다, 스키마 키 순서가 지켜진다,
  minimal 이면 `thoughtsTokenCount` 가 **실제로 빠진다**(`zeroIfAbsent` 근거), high 면 그 이름으로 온다.
  usageMetadata 에 문서에 없던 `serviceTier: "standard"` 가 온다 — Free Tier 인지는 응답에 드러나지 않는다
- [ ] push · PR · roadmap "걸어온 길" 행 (PR 번호가 필요)

## Resume Point
push 됨. PR → roadmap 행 (PR 번호로). 막힌 프롬프트(`blockReason`)의 실제 응답은 아직 못 봤다.
콘솔은 모델 선택이 `/api/models` 로 저절로 반영된다. 사용량 화면의 `gemini` 표기와 effort 칸은 정하지 않았다.

## 남은 결정 (뒤로 미룸)
- Free Tier 호출을 0 달러로 볼지 — 비용 대사 · 추적과 함께 정한다
- `/api/models` 에 모델별 effort 를 실어 콘솔이 고를 수 있게 할지

## Files
- SPEC.md — 확인한 사실, 결정, 설계, 테스트
