# Plan: audio-generate — 글을 음성으로 (TTS)
Created: 2026-11-22
Status: completed

면접 준비 서비스의 면접관 질문을 소리로 만든다. 요구는 `playground` 저장소의 `workbench-needs.md` A1 · A3 이고 **11/28 저녁이 기한**이다.
STT 는 PR #118~#121 로 끝났다. 사실 · 결정 · 설계는 SPEC.md.

## Progress
- [x] 조사 — Gemini 3.8 TTS 문서 + 실제 호출 2건(SPEC §1). 요청 · 말투 · 응답 · 사용량 모양 확인
- [x] 결정 D1~D7 (SPEC §2). 요청 키 · 결과 메타의 모양은 2026-11-22 에 확정
- [x] 한국어 질문의 음질을 사용자가 듣고 판단 (2026-11-22, `Kore` · `flash-tts` 기준 "비교적 괜찮다"). 목소리 · `flash-lite` 비교는 안 함
- [x] 429 한도 확인 — 키의 프로젝트가 **Free Tier, TTS 분당 3건**(429 본문). 문서에는 TTS 한도가 없다. 재시도는 기존 정책(간격 없이 최대 3회)을 그대로 쓴다 — 알려진 한계
- [x] PR A #123 — ADR 0006(채택) + 결과물 일반화. 응답 타입도 sealed 로 가름. Codex P1 두 건은 plan 파일 위치 지적이라 #118 처럼 두었다
- [x] PR B1 #124 (`feat/audio-provider`, A 위에 쌓음) — 포트 `ExternalApiAudioProvider` · WAV 길이 · Gemini Interactions 어댑터 · 목소리 30개. live 2건 통과
- [x] PR B2 #125 (`feat/audio-generate-run`, B1 위) — `audio-generate` 옵션 · 접수(모르는 목소리 400 + `availableVoices`) · 워커 · 원장 `AudioRequest` · 단가 V21 · `scripts/audio.sh`
- [x] PR C #126 (`feat/audio-console`, B2 위) — 모델 목록의 `voices` · 작업실 음성 탭 · 작업 내역의 오디오 재생 · 음성 청록 토큰
- [x] `docs/roadmap.md` 갱신 (T1 에서)

- [x] #123 ~ #126 머지(2026-11-22, Codex 리뷰: #125 지적 없음)
- [x] T1 TTS 템플릿(콘솔) — #128 (`feat/template-tts`)
- [x] T2 STT 템플릿 — #129. 실행 때 파일을 받는 칸 `inputFileCount` · `files`(옛 이름 `inputImageCount` · `images` 도 받음)
- [x] 배포 `v2026.11.22` — 배포 PR #130. 배포 직전 #106(인가 서버)의 서명 키 파일을 붙이는 볼륨이 매니페스트에 없는 것을 찾아 #131 로 먼저 넣었다. 서버 apply 는 사용자(2026-11-22 03:40Z), 기동 로그에 `gemini`, 운영 메타데이터 · JWKS 확인
- [x] 운영 TTS 1건 — 사용자가 콘솔에서 실행
- [x] 콘솔 UX — 디자인 시스템 오디오 플레이어 · 결과 파일 내려받기(`downloadUrl`) · 주소 복사 · 설명 줄 — #132. Codex P2 세 건(첨부 칸 안의 플레이어 클릭이 파일 선택 창을 엶 · 오래된 서명 주소로 내려받기 · 좁은 화면에서 넘침)을 고친 뒤 머지
- [x] 음성 고르기 안내 — #134. 목소리 특징 · 말투 예시 · 모델 안내 · 받아쓰기 프롬프트 예시(콘솔) + `docs/audio-guide.md`. Codex 지적 없음
- [x] 배포 `v2026.11.28` — 배포 PR #135(마이그레이션 · 새 설정 없음). 서버 apply 는 사용자

## Resume Point
끝났다. 음성(Gemini 글 · STT 첨부 · TTS · 음성 템플릿)이 `v2026.11.22` 로, 콘솔 플레이어 · 내려받기 · 음성 고르기 안내가 `v2026.11.28` 로 운영에 나갔다(apply 는 사용자).

남은 후속 — 이 plan 밖에서 필요해지면 한다.
- 운영 STT 1건 확인(Gemini 가 운영 보관소의 서명 주소를 직접 받아가는지). 못 받아가면 바이트로 다시 보내 결과는 나온다
- 429 재시도에 간격이 없다(Free Tier TTS 분당 3건). 서비스가 한꺼번에 여러 건을 부르면 Task 에 다음 시도 시각을 두는 것을 본다
- `gemini-3.8-flash-lite-tts` 는 실제로 부르지 않았다
- 배포 apply 에서 `statefulset/garage` · `statefulset/postgresql` 이 configured 로 나온 이유를 보지 않았다
- 목소리 미리듣기 샘플 — 사용자가 "언젠가" 로 둔 후보. 목소리 특징 한 단어는 Gemini 문서의 영어 원문을 옮긴 것이라 들어 보고 고칠 수 있다
- "링크 복사" 는 오래된 서명 주소를 갱신하지 않는다(내려받기만 갱신). 버튼의 안내 문구로만 알린다

## Files
- SPEC.md — 확인한 사실, 결정, 설계, 테스트
