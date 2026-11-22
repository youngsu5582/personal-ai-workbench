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
- [x] PR B1 #124 (`feat/audio-provider`, A 위에 쌓음) — 포트 `ExternalApiAudioProvider` · WAV 길이 · Gemini Interactions 어댑터 · 목소리 30개. 로컬 커밋까지. live 2건 통과(2026-11-22)
- [x] PR B2 #125 (`feat/audio-generate-run`, B1 위) — `audio-generate` 옵션 · 접수(모르는 목소리 400 + `availableVoices`) · 워커 · 원장 `AudioRequest` · 단가 V21 · `scripts/audio.sh`. 로컬 커밋까지
- [x] PR C #126 (`feat/audio-console`, B2 위) — 모델 목록의 `voices` · 작업실 음성 탭 · 작업 내역의 오디오 재생 · 음성 청록 토큰. 로컬 커밋까지, 브라우저 검사 36개 통과(모의 API)
- [x] `docs/roadmap.md` 갱신 (T1 에서)

- [x] #123 ~ #126 머지(2026-11-22, Codex 리뷰: #125 지적 없음)
- [~] T1 TTS 템플릿(콘솔) — `feat/template-tts`
- [ ] T2 STT 템플릿 — 실행 때 파일을 받는 칸 `inputFileCount` · `files`(옛 이름 `inputImageCount` · `images` 도 받음)
- [ ] 배포 — T2 뒤. 태그 → 배포 PR → 서버 apply(사용자) → 운영 STT · TTS 실호출 1건씩

## Resume Point
A~C(#123 ~ #126)는 `main` 에 있다. 배포는 템플릿 PR 둘(T1 TTS · T2 STT) 뒤에 한 번에 한다(사용자 결정, 메모리 `workbench-audio-deploy-plan`). 운영 Gemini 키는 Free Tier 그대로.
지금은 T1(`feat/template-tts`) — 콘솔 템플릿에 음성 종류(읽을 글 · 목소리 · 말투). 서버는 #125 로 이미 TTS 템플릿을 받는다.
다음 T2 — `TemplateSpec.inputImageCount` → `inputFileCount`(저장 JSON 은 `@JsonAlias` 로 옛 이름을 읽음), 실행 요청 `images` → `files`(옛 이름도 받음), 글 생성 템플릿이 실행 때 파일(오디오 · 이미지)을 첨부로 받음. 블로그가 템플릿 API 를 쓰므로 옛 이름은 계속 받는다.
11/28 저녁이 서비스 기한이다 — 배포가 그 전에 끝나야 한다.

## Files
- SPEC.md — 확인한 사실, 결정, 설계, 테스트
