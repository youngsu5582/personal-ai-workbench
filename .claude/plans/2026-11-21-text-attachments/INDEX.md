# Plan: text-generate 에 이미지 · 오디오를 함께 보낸다
Created: 2026-11-21
Status: active

`text-generate` 가 글 하나만 받던 것을, 올린 파일(이미지 · 오디오)을 함께 읽고 글을 만들게 한다.
"오디오를 받아 써 줘" 같은 STT 도 이 경로로 한다. 자막 · 타임스탬프가 필요한 전사는 따로 본다(SPEC §5).
그다음 순서는 TTS(`audio-generate`) 다. 이 plan 은 새 ADR 없이 ADR 0002 에 단가 경로 절만 더하고, TTS plan 에서 ADR 0006 을 쓴다(SPEC §5).

## Progress
- [x] 확인할 사실 (SPEC §1) — F1~F5 확인 (2026-11-21). 미검증: 서명 주소 · 이미지 형식 목록
- [x] 결정 D1~D4 (SPEC §2, 2026-11-21). D5 는 기본안(첨부 수). **D3 은 하지 않는다** (단가가 같다)
- [x] 값 닫기 (2026-11-21) — 15MB · wav/mp3/ogg/flac/m4a/aac · OpenAI 는 이미지만. 서명 주소 live 는 보관소가 닿는 환경에서
- [x] 업로드를 오디오까지 넓힌다 (PR 1)
- [x] `TextGenerateOption.attachments` · 포트 `inputTypes` · 접수 검증 (PR 2). 어댑터는 빈 집합 — 아직 싣지 않는다
- [x] 워커의 입력 경로 · 원장의 첨부 수(D5) (PR 3)
- [x] Gemini 어댑터의 파일 파트 · `inputTypes` (PR 3). OpenAI 는 텍스트 모델의 이미지 입력을 확인하지 못해 아직 빈 집합
- [x] live(F6) · development.md · scripts. 로컬 스모크(앱을 띄워 `ATTACH` 로 끝까지)는 아직

## Resume Point
세 PR(업로드 → 접수 → 실행)이 스택으로 구현됐다. 남은 것:
- push · PR 생성 · 머지(1 → 2 → 3 순서). `docs/roadmap.md` 는 PR 번호가 생기면 갱신한다.
- 로컬 스모크 — 앱을 띄우고 `upload.sh` → `ATTACH=<sha> text.sh` 로 끝까지. 로컬 Garage 는 Gemini 가 닿지 않아 바이트 재시도 경로를 탄다.
- 사람 녹음(한국어, m4a)으로 전사 품질 확인. 지금은 합성 음성뿐이다.
- 무음 · 말 없는 오디오는 STOP 인데 글이 없어 `응답에 글이 없다`(`PROVIDER_ERROR`, 재시도 대상)로 닫힌다 — 같은 오디오를 다시 보내 돈만 더 나갈 수 있다. 면접 서비스에서 침묵 구간이 흔하면 다룬다.
- OpenAI 텍스트 모델의 이미지 입력(`input_image`)과 `inputTypes` — OPENAI_LIVE 로 확인한 뒤.
- 서명 주소(보관소 presigned)를 Gemini 가 읽는지 — 보관소가 인터넷에서 닿는 환경에서만. 안 읽으면 늘 바이트로 간다.

## 남은 결정 (뒤로 미룸)
- 콘솔의 글 생성 화면에서 파일을 고르는 칸 — 백엔드가 먼저
- 템플릿이 첨부를 실행 입력으로 받게 할지

## Files
- SPEC.md — 확인할 사실, 결정할 것, 설계 초안, 테스트
