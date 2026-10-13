# Plan: README 를 프로젝트 소개로 다시 쓴다
Created: 2026-10-12
Status: completed

## Progress
- [x] 로컬 개발 안내를 docs/development.md 로 옮긴다 (내용 그대로, 링크 대상만)
- [x] README 를 소개(무엇 · 왜 · 하려는 것 · 설계 결정 · 어떻게 만드나 · 바라보는 곳)로 다시 쓴다
- [x] README 첫머리 그림 — 라이트 · 다크 두 장, `.claude/scripts/readme-overview.cjs` 로 다시 뽑는다
- [x] docs/roadmap.md — 지금 어디까지 + 머지된 PR 로 걸어온 길
- [x] 코드와 갈라진 문서 정리 — 도메인 문서 줄이기, 모듈 문서 시점 표시, 첫 계획 삭제, CLAUDE.md 의 H2
- [x] PR 머지 (#52)

## 결정
- 구성은 2024-corea README 를 따른다. 밖을 향한 글이라 해요체로 쓴다
- 첫 문장은 "내 서비스들이 함께 쓰는 AI 계층". 하려는 것이 모두 이 문장 안에 들어간다
- 로드맵에 날짜를 약속하지 않는다. 밀리면 멈춘 프로젝트로 읽힌다
- 상태는 docs/roadmap.md 한 곳에 둔다. 다른 문서의 "현재 구현 / 다음 구현" 은 지웠다
- 저장소 이름은 personal-ai-workbench 를 유지한다. 바꿔도 GitHub 가 옛 주소를 넘겨주므로 서두를 이유가 없다

## Resume Point
다음 PR — 문서가 낡지 않게 하는 장치. 기계적 검사는 "이름이 사라짐 · 링크 깨짐" 만 잡는다는 것을 전제로 한다.
1. CLAUDE.md 에 문서 기준: 문서의 문장은 (이름을 가리킨다 | 코드에서 생성된다 | 이유다) 셋 중 하나. 줄 번호로 가리키지 않는다
2. `DocumentationTest` — README · CLAUDE.md · AGENTS.md · docs/ 의 상대 링크와 백틱 속 이름(클래스 · 클래스.메서드 · 테스트 이름 · 컬럼)을 ArchUnit 과 jOOQ 생성 스키마에 대조. 컬럼은 마이그레이션 SQL 검색으로는 안 된다(지운 컬럼도 옛 파일에 남는다)
3. main 브랜치 보호 — 지금 꺼져 있어 CI 가 빨개도 머지된다
4. convention-gate 에 문서 검사 kind 추가 (AI 가 쓰는 순간 · 코드 이름을 바꾸는 순간)
5. (선택) Stop 훅, Modulith Documenter 로 모듈 그림 생성, 걸어온 길을 PR 목록에서 생성

그 밖에: "왜 만들게 되었나요" 를 본인 말로, 그림의 서비스 A · B 를 실제 이름으로, decision API 한 줄 설명.
