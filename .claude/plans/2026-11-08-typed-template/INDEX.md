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
- V18/V19 는 #83 안에서 새 V18 하나로 재구성(운영 미적용). 기본 모델 컬럼도 같은 V18 에 넣었다.
- 콘솔 디자인 시스템: 미니멀 톤(Linear·Vercel 류), shadcn/ui 방식 + Tailwind v4(preflight 끔) + Radix. 기반 + 템플릿 화면을 #95 로, 다른 화면은 이어지는 PR. 작업 내역 화면 PR(#94)은 #95 뒤로 미뤄져 있다.
- `gpt-image-2` edits 실호출 검증은 화면 확인 때 1회 (그 전엔 가짜 Provider 로만).

## Progress
- [x] #92 셀프 체크 · 수정 · 머지 (2026-11-08, merge `684a8ad`)
- [x] #83 재작업 + 기본 모델 (head `52b400b`, 전체 493개 · CI 통과)
- [x] #91 rebase · 새 계약 · 종류별 편집 · 변수 강조 · 미리보기 · 기본 모델 (head `0237da4`, CI 통과)
- [x] #95 디자인 시스템 기반 + 템플릿 화면 (CI 통과)
- [ ] 사용자 화면 확인 → 머지 순서 #83 → #91 → #95
- [ ] 이미지 업로드 흐름 로컬 확인(Garage S3 로 기동 필요 — local 보관소는 업로드 501)
- [ ] 다른 화면 디자인 시스템 이전(대시보드 · 작업 내역 · 사용량 · 서비스 · API 키), #94 재개
- [ ] 판정 질문 편집기 UI (별도 PR)

## Resume Point
사용자가 http://localhost:8080 (임시 DB `wb-template-preview-pg` 5434, 보관소 local, #95 워크트리 `console-design-system` 에서 bootRun)으로 화면을 확인하는 중.
피드백을 #95 에 반영한다. 스택 PR 이라 머지는 merge-async 가 필요할 수 있다 — 머지 전에 `.stack` 을 본다.

## Files
- INDEX.md — 이 문서
