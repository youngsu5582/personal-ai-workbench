# 로드맵

## 지금 어디까지

README 의 [이런 걸 하려고 해요](../README.md#이런-걸-하려고-해요)가 지금 어디까지 왔는지예요.

| 하려는 것 | 지금 |
|---|---|
| 🧩 어떤 AI 기능이든 요청 하나로 | 🚧 이미지(T2I · I2I)만 |
| ⏳ 맡겨두고, 결과는 나중에 | ✅ 비동기 처리 · 재시도 — 중복 요청 방지는 아직 |
| 💰 요청 하나하나의 비용까지 | 🚧 호출마다 사용량 기록 · 이번 달 조회 — 단가표는 아직 |
| 📦 급하지 않으면 더 싸게 | ⬜ |
| 📝 자주 하는 요청은 템플릿으로 | ⬜ |
| 🔐 내 서비스들이 안전하게 연결 | 🚧 Google 로그인 · 자체 토큰까지 — OAuth Server 는 아직 |

그다음에는 MCP 도구, Webhook 과 SSE 로 실시간 상태, 운영 통계, 멀티 모듈과 Kubernetes 를 보고 있어요.

## 걸어온 길

2026년, 머지된 PR 기준이에요.

| 언제 | 한 일 | PR |
|---|---|---|
| 09.13 | 사용자와 로그인 수단을 나누고, Google 로그인으로 자체 토큰을 발급 | [#2](https://github.com/youngsu5582/personal-ai-workbench/pull/2) [#3](https://github.com/youngsu5582/personal-ai-workbench/pull/3) [#4](https://github.com/youngsu5582/personal-ai-workbench/pull/4) |
| 09.14 – 09.19 | 첫 생성 — T2I 요청 접수부터 OpenAI 호출, 결과 보관, 진행 조회까지 | [#7](https://github.com/youngsu5582/personal-ai-workbench/pull/7) [#8](https://github.com/youngsu5582/personal-ai-workbench/pull/8) [#9](https://github.com/youngsu5582/personal-ai-workbench/pull/9) [#14](https://github.com/youngsu5582/personal-ai-workbench/pull/14) [#15](https://github.com/youngsu5582/personal-ai-workbench/pull/15) |
| 09.20 | 기반 다지기 — 스키마는 마이그레이션이, 테스트는 진짜 PostgreSQL 에서, PR 마다 CI | [#17](https://github.com/youngsu5582/personal-ai-workbench/pull/17) [#18](https://github.com/youngsu5582/personal-ai-workbench/pull/18) [#19](https://github.com/youngsu5582/personal-ai-workbench/pull/19) [#21](https://github.com/youngsu5582/personal-ai-workbench/pull/21) |
| 09.24 – 09.26 | Provider 호출마다 지출을 기록하고, S3 호환 보관소를 붙임 | [#31](https://github.com/youngsu5582/personal-ai-workbench/pull/31) [#32](https://github.com/youngsu5582/personal-ai-workbench/pull/32) [#33](https://github.com/youngsu5582/personal-ai-workbench/pull/33) [#35](https://github.com/youngsu5582/personal-ai-workbench/pull/35) |
| 10.05 – 10.12 | 직접 업로드 주소 발급, 이미지 투 이미지, 올린 이미지를 I2I 입력으로 | [#36](https://github.com/youngsu5582/personal-ai-workbench/pull/36) [#39](https://github.com/youngsu5582/personal-ai-workbench/pull/39) [#46](https://github.com/youngsu5582/personal-ai-workbench/pull/46) |
| 10.09 – 10.12 | 관례를 기계로 — 이름 · 자리 규칙을 테스트와 Claude Code Mod 가 강제 | [#40](https://github.com/youngsu5582/personal-ai-workbench/pull/40) [#41](https://github.com/youngsu5582/personal-ai-workbench/pull/41) [#43](https://github.com/youngsu5582/personal-ai-workbench/pull/43) |
