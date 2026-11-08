# Plan: 작업 종류별 생성 템플릿
Created: 2026-11-08
Status: active

## 정해진 것
- 템플릿 = 변수 자리가 있는 저장된 `GenerationOption`. 템플릿 하나에 종류 하나, 생성 후 고정.
- 모델은 템플릿에 저장하지 않는다. 실행 때 `/api/models?type=<템플릿 종류>` 에서 고른다.
- 실행 요청의 런타임 `prompt` 를 없앤다. 실행마다 바뀌는 글은 모두 `{{변수}}` 다. 실행 요청은 종류와 무관하게 `variables · model · taskCount` (+ 입력 이미지).
- 변수는 글 필드(prompt · instructions · 근거 text · description)에만. 식별자(name · value · label)와 크기·품질은 변수 불가.
- 변수 계약은 #83 그대로: 추출한 이름 집합 = 전달 키 집합, 빈 값 허용, 치환 결과가 도메인 규칙 위반이면 400.
- 이미지: 템플릿에 고정 참조 이미지 저장 가능(예: 블로그 썸네일 생성) + 실행 때 입력 이미지(예: 썸네일 스타일 수정).
- #92 는 셀프 체크 후 머지.
- V18/V19 를 #83 안에서 새 V18 하나로 재구성한다 (운영 미적용 확인됨. 새 PR 아님. force-push 없이 커밋을 쌓는다).
- 실행 입력 이미지 수는 템플릿이 `inputImageCount` 로 선언한다. 실행 때 개수가 다르면 400.
  이미지 종류는 고정 sources + inputImageCount <= `ImageOption.MAX_SOURCES` 를 저장 시점에 검사한다. 판정은 실행 이미지를 근거 뒤에 붙인다. 텍스트는 0 만.
- `gpt-image-2` edits 실호출 검증은 #91 화면 확인 때 1회 (그 전엔 가짜 Provider 로만).

## Progress
- [x] #92 셀프 체크 · 수정 · 머지 (2026-11-08, merge `684a8ad`)
- [x] #83 재작업: 엔티티 `option jsonb` · V18 재구성 · 치환(sealed when) · 실행 API · 입력 이미지 · 테스트 (로컬 전체 490개 통과)
- [ ] #91: main + #83 병합, 종류 선택 · 종류별 옵션 편집 · 모델 목록 연동 · 입력 이미지 업로드
- [ ] 판정 질문 편집기 UI (별도 PR)
- [ ] 사용자 화면 확인 → #83 → #91 머지

## Resume Point
#83 push · CI 확인 뒤 #91 로 넘어간다: #91 워크트리(`personal-ai-workbench-source-worktrees/template-console`)에서
main 과 feat-user-template 을 병합하고, 콘솔을 새 템플릿 계약(option · variables · inputImageCount · images)에 맞춘다.
#91 의 `TemplateBrowserE2eTest` 도 새 계약으로 고쳐야 한다.

## Files
- INDEX.md — 이 문서
