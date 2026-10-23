# Plan: 서명 다운로드 캐시 정책
Created: 2026-10-23
Status: completed

## Progress
- [x] 서명 GET과 앱 다운로드 응답에 캐시 금지 정책 적용
- [x] Garage 응답 및 서명 변조 검증
- [x] 배포 런북에 캐시 우회·검증 절차 추가 및 한국어 번역
- [x] 전체 테스트 413개 통과

## Resume Point
홈서버 배포 시 실제 Cloudflare를 거쳐 캐시 우회와 URL 만료를 확인한다.

## Files
- `src/main/kotlin/dev/joyson/aiworkbench/storage/s3/S3PresignedUrlIssuer.kt` — 서명 GET 응답 정책
- `src/main/kotlin/dev/joyson/aiworkbench/generation/api/GeneratedFileController.kt` — 앱 다운로드 응답 정책
- `deploy/README.md` — 홈서버 배포와 검증 절차
