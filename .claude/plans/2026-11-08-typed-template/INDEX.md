# Plan: 작업 종류별 생성 템플릿
Created: 2026-11-08
Status: active

## 정해진 것
- 템플릿 = 변수 자리가 있는 저장된 `GenerationOption`. 템플릿 하나에 종류 하나, 생성 후 고정.
- 템플릿은 **기본 모델**을 저장한다(선택). 실행 요청이 모델을 주면 그쪽, 없으면 기본 모델, 둘 다 없으면 400. 기본 모델은 저장할 때 종류를 다루는지 확인한다.
- 실행 요청의 런타임 `prompt` 를 없앤다. 실행마다 바뀌는 글은 모두 `{{변수}}` 다. 실행 요청은 종류와 무관하게 `variables · images · model · taskCount`.
- 변수는 글 필드(prompt · instructions · 근거 text · description)에만. 식별자(name · value · label)와 크기·품질은 변수 불가.
- 변수 계약: 추출한 이름 집합 = 전달 키 집합, 빈 값 허용, 치환 결과가 도메인 규칙 위반이면 400.
- 이미지: 템플릿에 고정 참조 이미지 저장 가능(예: 블로그 썸네일 생성) + 실행 때 입력 이미지(예: 썸네일 스타일 수정).
  실행 입력 이미지 수는 `inputImageCount` 로 선언, 고정 sources + inputImageCount <= `ImageOption.MAX_SOURCES` 를 저장 시점에 검사. 판정은 실행 이미지를 근거 뒤에 붙인다. 텍스트는 0 만.
- V18/V19 는 #83 안에서 새 V18 하나로 재구성(운영 미적용). option · 받을 이미지 수 · 기본 모델은 함께 검증되고 DB 가 조회에 쓰지 않아 실행 명세 `TemplateSpec`(jsonb `spec`) 한 칸에 담는다. 변수 채우기(`fill`)도 명세가 갖는다.
- 콘솔 디자인 시스템: 미니멀 톤(Linear·Vercel 류), shadcn/ui 방식 + Tailwind v4(preflight 끔) + Radix. 기반 + 템플릿 화면을 #95 로, 다른 화면은 이어지는 PR. 작업 내역 화면 PR(#94)은 #95 뒤로 미뤄져 있다.
- `gpt-image-2` edits 실호출 검증은 화면 확인 때 1회 (그 전엔 가짜 Provider 로만).

## Progress
- [x] #92 셀프 체크 · 수정 · 머지 (2026-11-08, merge `684a8ad`)
- [x] #83 재작업 + 기본 모델 (head `52b400b`, 전체 493개 · CI 통과)
- [x] #91 rebase · 새 계약 · 종류별 편집 · 변수 강조 · 미리보기 · 기본 모델 (head `0237da4`, CI 통과)
- [x] #95 디자인 시스템 기반 + 템플릿 화면 (CI 통과)
- [x] #96 콘솔 전 화면 이전 · 옛 CSS 제거 · preflight · 업로드 안내 (draft)
- [x] 이미지 업로드 로컬 확인 — Garage(S3) 로 띄워 업로드 → 미리보기 → 저장까지 브라우저로 확인. local 보관소는 업로드를 받지 않고 안내만 한다(코드 변경 없음으로 결정)
- [ ] 사용자 화면 확인 → 머지 순서 #83 → #91 → #95 → #96
- [ ] 저장한 템플릿의 참조 이미지 미리보기 — #93 머지 뒤 `FileSourceResolver.issuePreviewUrls` 로 템플릿 응답에 붙인다(별도 조회 API 는 만들지 않는다)
- [ ] #94(작업 내역 입력 · 결과 함께 보기) 를 #96 화면 위에서 다시 얹기 — 다른 세션 작업
- [ ] 원본 비율 따르기 · 이미지 처리 위치 ADR — 헤더만 읽기(라이브러리)로 방향만 정함, 보류
- [ ] 판정 질문 편집기 UI (별도 PR)

## Resume Point
사용자가 http://localhost:8080 으로 화면을 확인하는 중. 로컬 서버는 #96 워크트리 `console-screens` 에서 bootRun,
임시 DB `wb-template-preview-pg`(5434), 보관소는 Garage(`personal-ai-workbench-garage`, 3900, CORS localhost:8080 설정됨).
피드백은 #96(화면) · #95(기반) 에 반영한다. 스택 PR 이라 머지 전에 `.stack` 을 본다.

## Files
- INDEX.md — 이 문서
