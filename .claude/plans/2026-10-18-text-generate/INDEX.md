# Plan: text-generate — 텍스트를 만들고, 원하면 JSON Schema 대로 받는다
Created: 2026-10-18
Status: completed

#65(이미지 종류를 `image-generate` 하나로) 다음 단계다. 종류는 결과물로 가른다.

## 결정 (2026-10-18)
- 종류는 `text-generate` 하나 — 입력으로도, 형식 없는 글 / 구조화된 답으로도 나누지 않는다
- 요청은 기존 `POST /api/jobs` 비동기 그대로. 재시도 · 원장 · 비용 · 지표를 다시 쓴다. 동기 경로는 필요해지면
- 옵션: `prompt` · `instructions?` · `output`(`text` 기본 / `json-schema` + `schema`)
- 결과는 새 테이블 `generated_texts` — `generated_files` 와 대칭. Task 는 호출, 결과물은 따로
- 스키마와 답은 **원문 문자열**로 저장한다. jsonb 는 키를 다시 늘어놓는데 출력 키는 스키마의 키 순서대로 나온다
  (OpenAI 문서 명시). 그래서 조회 응답의 `option.output.schema` 는 문자열로 돌아온다
- 응답 `texts[].content`: text 면 문자열, json-schema 면 파싱한 객체
- 텍스트 포트를 이미지 포트와 따로 둔다 — 두 모델 군이 서로의 일을 못 해 `supports(model)` 하나로 접수 때 못 거른다
- 거절 · 잘림은 예외가 아니라 `StopReason` 으로 답한다(과금된 응답이라 사용량을 지킨다). 워커가 Task 를 재시도 없이 닫고 지출은 성공 호출로 적는다
- OpenAI Responses API `/v1/responses`, `strict: true`, `store: false`, 스키마 이름은 `output` 고정
- `effort`(none · low · medium · high · xhigh · max)는 선택. 빼면 보내지 않아 모델 기본값(대개 medium). 모델마다 받는 값이 달라 허용표 없이 저쪽 거절을 Task 실패로 남긴다. 원장 `TextRequest.effort` 에 보낸 값을 적는다
- 모델: gpt-6-luna · gpt-5.6-terra(둘 다 실호출 확인) · gpt-6.1-sol · gpt-6-astra(미검증). 단가표는 캐시 할인 없이 → 캐시가 걸린 호출은 비싸게 잡힌다

## Progress
- [x] OpenAI 실패 해석 · 번역을 이미지 · 텍스트가 함께 쓰도록 분리 (refactor)
- [x] provider 포트 · OpenAI 어댑터 · 레지스트리 `findText`
- [x] domain · 워커 텍스트 경로 · `generated_texts`(V12) · 조회 응답 `texts` · 원장 `TextRequest`
- [x] 단가표 V13 + 어댑터 모델마다 단가표가 있는지 시드 테스트
- [x] fix: 255자 넘는 실패 사유가 Task 를 RUNNING 에 묶던 것(기존 버그, 이미지에도 해당)
- [x] scripts/text.sh · job.sh 글 출력 · development.md · README · roadmap
- [x] main(#65 머지) 위로 rebase · `feat/text-generate` push
- [x] live 확인 (gpt-6-luna, 2회) — 형식 없는 글 · strict 스키마 키 순서 통과. usage 에 단가표 경로(input_tokens · output_tokens)가 있다.
  처음 보는 `input_tokens_details.cache_write_tokens` 가 온다(0). 단가가 따로 붙는지는 가격표에 없어 모른다
- [x] PR #66 — roadmap "걸어온 길" 행(#65 · #66)도 이 PR 에 넣었다. 리뷰를 가볍게 하려고 refactor · fix 는 #67 로 뗐다

## Resume Point
#67(refactor · fix)은 10-18 머지됐다. 나머지는 GitHub 스택(size 3)으로 나눴다 — ① #68 포트 · 어댑터 → ② #69 글 결과물 자리 → ③ #66 text-generate. 머지는 아래부터 merge-async REST 로 하고(`gh pr merge` 는 거절된다), 위 PR 은 GitHub 가 rebase 해 SHA 가 바뀌니 로컬을 원격에 맞춘다. 남은 후보: 캐시 입력 할인을 매길 수 있게 단가 계산 확장, `cache_write_tokens` 단가 확인, sol · astra 실호출.

## Files
- INDEX.md — 이 파일
