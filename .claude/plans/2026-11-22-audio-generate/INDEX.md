# Plan: audio-generate — 글을 소리로 (TTS)
Created: 2026-11-22
Status: active

면접 준비 서비스의 면접관 질문을 소리로 만든다. 요구는 `playground` 저장소의 `workbench-needs.md` A1 · A3 이고 **11/28 저녁이 기한**이다.
STT 는 PR #118~#121 로 끝났다. 사실 · 결정 · 설계는 SPEC.md.

## Progress
- [x] 조사 — Gemini 3.8 TTS 문서 + 실제 호출 2건(SPEC §1). 요청 · 말투 · 응답 · 사용량 모양 확인
- [x] 결정 D1~D7 (SPEC §2). 요청 키 · 결과 메타의 모양은 2026-11-22 에 확정
- [x] 한국어 질문의 음질을 사용자가 듣고 판단 (2026-11-22, `Kore` · `flash-tts` 기준 "비교적 괜찮다"). 목소리 · `flash-lite` 비교는 안 함
- [x] 429 한도 확인 — 키의 프로젝트가 **Free Tier, TTS 분당 3건**(429 본문). 문서에는 TTS 한도가 없다. 재시도는 기존 정책(간격 없이 최대 3회)을 그대로 쓴다 — 알려진 한계
- [x] PR A (#123) — ADR 0006 + 결과물 일반화 (메타 표기, 조회 응답의 형식 · 길이). 기능 변화 없음. 리뷰 · 머지 대기
- [~] PR B1 (`feat/audio-provider`, A 위에 쌓음) — 포트 `ExternalApiAudioProvider` · WAV 길이 · Gemini Interactions 어댑터 · 목소리 30개. 로컬 커밋까지, **live 미실행**(승인 필요)
- [ ] PR B2 — `audio-generate` 옵션 · 접수(모르는 목소리 400) · 워커 · 원장 · 단가 마이그레이션
- [ ] PR C — 콘솔: 작업 내역의 오디오 재생, 작업실의 음성 탭
- [ ] `docs/roadmap.md` 갱신 (PR 번호가 생긴 뒤)

## Resume Point
PR 은 4개다: A(#123) → B1 → B2 → C. 브랜치는 이 워크트리에서 `git checkout -b` 로 차례로 쌓는다(A `feat/audio-generate` → B1 `feat/audio-provider` → …).
**B1 은 로컬 커밋까지 됐다.** live(`GeminiAudioProviderLiveTest`, 프로브를 대체)는 아직 돌리지 않았다 — 새 호출이라 승인을 받는다. Free Tier 라 출력은 무료지만 분당 3건이다.
다음은 **B2** — `AudioGenerateOption`(`text` · `voice` · `style`) · `GenerationJobType.AUDIO_GENERATE` · 접수의 목소리 확인(`availableVoices` 400) · `TaskWorker.processAudio` · 원장 `AudioRequest` · 단가 V21.
이 워크트리의 `.env` 는 `text-attach-run` 의 것을 복사했다. Claude 는 `.env` 를 읽지 못하므로 live 는 테스트 코드가 읽는다.
11/28 저녁까지 B2 가 `main` 에 없으면 서비스 쪽은 원래 방식으로 간다(`playground` 의 INDEX). C(콘솔)는 기한 밖이다.

## Files
- SPEC.md — 확인한 사실, 결정, 설계, 테스트
