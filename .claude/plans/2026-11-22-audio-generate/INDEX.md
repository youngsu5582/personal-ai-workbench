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
- [x] PR A (#123) — ADR 0006 + 결과물 일반화. 응답 타입도 sealed 로 가름(852063b). 리뷰 · 머지 대기. Codex P1 두 건은 plan 파일 위치 지적이라 #118 처럼 두었다
- [~] PR B1 (`feat/audio-provider`, A 위에 쌓음) — 포트 `ExternalApiAudioProvider` · WAV 길이 · Gemini Interactions 어댑터 · 목소리 30개. 로컬 커밋까지. live 2건 통과(2026-11-22)
- [~] PR B2 (`feat/audio-generate-run`, B1 위) — `audio-generate` 옵션 · 접수(모르는 목소리 400 + `availableVoices`) · 워커 · 원장 `AudioRequest` · 단가 V21 · `scripts/audio.sh`. 로컬 커밋까지
- [~] PR C (`feat/audio-console`, B2 위) — 모델 목록의 `voices` · 작업실 음성 탭 · 작업 내역의 오디오 재생 · 음성 청록 토큰. 로컬 커밋까지, 브라우저 검사 36개 통과(모의 API)
- [ ] `docs/roadmap.md` 갱신 (PR 번호가 생긴 뒤)

## Resume Point
A(#123)는 열려 있고, **B1 · B2 · C 는 로컬 커밋까지 됐다**(이 워크트리의 `feat/audio-provider` → `feat/audio-generate-run` → `feat/audio-console`, 각각 아래 브랜치 위에 쌓임). live 2건 · 로컬 스모크 1건 통과(SPEC F13).
**다음: B1 · B2 · C push 와 PR 생성 — 사용자 승인을 기다린다.** PR 은 각각 아래 브랜치를 base 로 연다. 머지 전에는 `.stack` 을 본다(메모리 `github-stacked-pr-merge`).
스모크의 비용 행이 NULL 인 것은 호출(11-21 17:07 UTC)이 단가표 시작(11-22 00:00 UTC)보다 앞서서다. 정상 동작이다.
`.env` 는 `text-attach-run` 의 것을 복사했다. `var/keys/authserver-signing-key.pem` 은 이 워크트리용으로 만들었다(gitignore).
11/28 저녁까지 B2 가 `main` 에 없으면 서비스 쪽은 원래 방식으로 간다(`playground` 의 INDEX).

## Files
- SPEC.md — 확인한 사실, 결정, 설계, 테스트
