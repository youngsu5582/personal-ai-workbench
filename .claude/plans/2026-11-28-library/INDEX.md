# Plan: 라이브러리 — 생성 결과 파일 모아 보기
Created: 2026-11-28
Status: active

## 결정
- 데이터: 서버에 `GET /api/files?kind=image|audio&page&size` 추가 (작업 단위 페이지는 텍스트 작업이 많으면 빈 페이지가 생긴다)
- `kind` 는 늘어나는 enum — 비디오가 오면 상수 하나 + `FileResponse.Video` 하나
- 텍스트는 제외(다른 테이블, 격자로 훑기 부적합). 탭은 `이미지 | 오디오`, '전체' 없음
- 1단계 범위: 정사각 격자 · 날짜 묶음 · hover 캡션 · 길이 배지 · 오디오 목록. 라이트박스는 다음 PR

## Progress
- [x] 서버: Finder 메서드 · Service · Controller · 테스트 (전체 627 통과)
- [x] 콘솔: features/library 화면
- [x] 브라우저 테스트(모의 API) 49 통과 + Playwright 스크린샷
- [x] docs/roadmap.md
- [ ] 사용자 확인 → 커밋 3개(서버 API · 콘솔 화면 · 로드맵) → PR
- [ ] 다음 PR: 라이트박스(← → 이동)

## Resume Point
미커밋 상태. 사용자 화면 확인 뒤 커밋·PR. 실데이터 E2E(로컬 서버+DB) 미실행.

## Files
- INDEX.md — 이 파일
