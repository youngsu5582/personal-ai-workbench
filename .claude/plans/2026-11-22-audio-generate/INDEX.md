# Plan: audio-generate — 글을 소리로 (TTS)
Created: 2026-11-22
Status: active

면접 준비 서비스의 면접관 질문을 소리로 만든다. 요구는 `playground` 저장소의 `workbench-needs.md` A1 · A3 이고 **11/28 저녁이 기한**이다.
STT 는 PR #118~#121 로 끝났다. 사실 · 결정 · 설계는 SPEC.md.

## Progress
- [x] 조사 — Gemini 3.8 TTS 문서 + 실제 호출 2건(SPEC §1). 요청 · 말투 · 응답 · 사용량 모양 확인
- [x] 결정 D1~D7 (SPEC §2). 요청 키 · 결과 메타의 모양은 2026-11-22 에 확정
- [ ] 한국어 질문의 음질을 듣고 판단 · 429 한도 확인 (SPEC §6)
- [ ] PR A — ADR 0006 + 결과물 일반화 (메타 표기, 조회 응답의 형식 · 길이). 기능 변화 없음
- [ ] PR B — `audio-generate`: 옵션 · 포트 · Gemini Interactions 어댑터 · 접수 · 워커 · 단가 · 목소리 목록 · live
- [ ] PR C — 콘솔: 작업 내역의 오디오 재생, 작업실의 음성 탭
- [ ] `docs/roadmap.md` 갱신 (PR 번호가 생긴 뒤)

## Resume Point
SPEC §6 의 음질 확인부터. `GeminiTtsProbeLiveTest`(미커밋 탐색용 테스트)가 `build/live-test/tts-*.wav` 를 만든다 — 사람이 듣는다.
프로브의 요청 본문(F2 · F3)이 어댑터의 시작점이다. 어댑터를 만들면 프로브는 어댑터의 live 테스트로 바꾼다.
`.env` 는 이 워크트리에 있다(`text-attach-run` 의 것을 복사). Claude 는 읽지 못하므로 live 는 테스트 코드가 읽는다.

## Files
- SPEC.md — 확인한 사실, 결정, 설계, 테스트
