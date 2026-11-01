# Plan: 서비스 API 키 관리
Created: 2026-11-01
Status: completed

## Progress
- [x] 서비스 소유자 전용 키 발급 · 목록 · 폐기
- [x] PostgreSQL 통합 테스트 및 전체 테스트 확인

## Resume Point
키 인증 연결은 데이터 격리 후 별도 작업으로 진행한다.

## Files
- `src/main/kotlin/dev/joyson/aiworkbench/auth/` — 키 관리
- `src/main/kotlin/dev/joyson/aiworkbench/workspace/` — 서비스 소유권 확인
