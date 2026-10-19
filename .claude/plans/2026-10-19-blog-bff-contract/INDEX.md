# Plan: 블로그 BFF 가 Workbench 로 글 생성을 맡기게 한다
Created: 2026-10-19
Status: active

블로그 명세(Homelab 09 문서) 7절의 Workbench 선행 계약을 지금 코드와 대조해 요구사항 · 순서를 세웠다.
text-generate(#66)로 실행 계층은 있다. 남은 것은 서비스 주체 · 멱등 · 오류 코드 · 요청 추적이다.

## 결정 (2026-10-19)
- 사용자가 소유 · 과금 · 관리의 최상위. 서비스는 사용자가 만드는 호출자, API Key 는 서비스에 딸림 → ADR 0005(제안)
- 사람과 서비스는 같은 API, 자격 증명이 주체(소유 사용자 · 호출자 · 권한)를 정한다
- 키 단위가 아니라 서비스 단위로 기록 · 권한 — 키를 바꿔도 기록이 이어진다
- 템플릿도 사용자 소유. 그룹은 나중 — "누구 것인가"와 "누가 불렀나"를 나눠 둔 것이 그때의 이음매
- 응답에 Provider 이름은 넣지 않는다(R1.3 삭제). requestId 는 R5

## Progress
- [x] 요구사항 · 작업 순서 정리 → requirements.md
- [x] ADR 0005 초안(제안)
- [ ] 미확정 결정 ① ② ④ ⑦
- [ ] 블로그 명세 5 · 7절 수정
- [ ] A1 오류 · 추적
- [ ] B 서비스와 API Key (ADR 0005 채택)
- [ ] A2 멱등 · 대기

## Resume Point
미확정 결정 ① ② ④ ⑦ 을 사용자에게 받는다. 그다음 A1 plan.

## Files
- INDEX.md — 이 파일
- requirements.md — 요구사항 · 결정 · 작업 순서
- ADR: docs/adr/0005-principals-and-ownership.md
