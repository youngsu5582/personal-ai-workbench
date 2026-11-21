# Plan: audio-generate — 글을 소리로 (TTS)
Created: 2026-11-22
Status: active

면접 준비 서비스의 면접관 질문을 소리로 만든다. 요구는 `playground` 저장소의 `workbench-needs.md` A1 · A3 이고 **11/28 저녁이 기한**이다.
STT 는 PR #118~#121 로 끝났다. 사실 · 결정 · 설계는 SPEC.md.

## Progress
- [x] 조사 — Gemini 3.8 TTS 문서 + 실제 호출 2건(SPEC §1). 요청 · 말투 · 응답 · 사용량 모양 확인
- [x] 결정 D1~D7 (SPEC §2). 요청 키 · 결과 메타의 모양은 2026-11-22 에 확정
- [x] 한국어 질문의 음질을 사용자가 듣고 판단 (2026-11-22, `Kore` · `flash-tts` 기준 "비교적 괜찮다"). 목소리 · `flash-lite` 비교는 안 함
- [ ] 429 한도 확인 (SPEC §6) — 어댑터 단계에서 재시도 정책과 함께
- [ ] PR A — ADR 0006 + 결과물 일반화 (메타 표기, 조회 응답의 형식 · 길이). 기능 변화 없음
- [ ] PR B — `audio-generate`: 옵션 · 포트 · Gemini Interactions 어댑터 · 접수 · 워커 · 단가 · 목소리 목록 · live
- [ ] PR C — 콘솔: 작업 내역의 오디오 재생, 작업실의 음성 탭
- [ ] `docs/roadmap.md` 갱신 (PR 번호가 생긴 뒤)

## Resume Point
**PR A 부터 구현한다** — ADR 0006 + 결과물 일반화(SPEC §3 의 "바뀌는 곳"). 기능 변화 없이 이미지는 그대로 돈다.
`GeminiTtsProbeLiveTest`(미커밋, 탐색용)의 요청 본문(SPEC F2 · F3)이 PR B 어댑터의 시작점이다. 어댑터를 만들면 프로브는 어댑터의 live 테스트로 바꾼다.
이 워크트리의 `.env` 는 `text-attach-run` 의 것을 복사했다(메인 저장소의 `.env` 에는 Gemini 키가 없어 live 가 조용히 건너뛰어진다). Claude 는 `.env` 를 읽지 못하므로 live 는 테스트 코드가 읽는다.
live(Gemini TTS) 호출은 2026-11-22 에 사용자가 승인했다(1~3건 단위). 새 호출은 다시 묻는다.
11/28 저녁까지 PR B 가 `main` 에 없으면 서비스 쪽은 원래 방식으로 간다(`playground` 의 INDEX).

## Files
- SPEC.md — 확인한 사실, 결정, 설계, 테스트
