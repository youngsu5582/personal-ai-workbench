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
- [ ] 워커의 입력 경로 · 원장의 첨부 수(D5) (PR 3)
- [ ] Gemini 어댑터의 파일 파트 · `inputTypes` 채우기 (OpenAI 는 이미지만) (PR 3)
- [ ] live · 로컬 스모크 · development.md · scripts

## Resume Point
PR 3 — 워커가 첨부를 실제로 싣는다. `TaskWorker.processText` 를 `callWithInputs` 로 감싸고, `ProviderRequestMapper` · Gemini 어댑터의 파일 파트 · `inputTypes` 를 채운다.
`keep/gemini-file-parts` 브랜치에 DTO(`inlineData` · `fileData`)와 live 테스트 2건이 있다 — PR 3 에서 가져온다.
PR 3 의 live 에서 M4A 의 MIME(`audio/mp4`)을 Gemini 가 받는지 확인한다. 안 받으면 업로드 계약(`FileContentType`)부터 고친다.
서명 주소(보관소 presigned)를 Gemini 가 읽는지는 보관소가 인터넷에서 닿는 환경에서만 확인된다 — 안 읽으면 늘 바이트로 보낸다(D4).
live 호출(`GEMINI_LIVE=1`)은 사용자가 승인했다(2026-11-21). `.env` 는 Claude 가 읽지 못하므로 live 는 테스트 코드가 읽게 한다.
PR 2 가 머지된 상태로 배포하면 접수는 항상 400 이다(어댑터가 아무 형식도 받지 않는다). 동작은 PR 3 부터다.

## 남은 결정 (뒤로 미룸)
- 콘솔의 글 생성 화면에서 파일을 고르는 칸 — 백엔드가 먼저
- 템플릿이 첨부를 실행 입력으로 받게 할지

## Files
- SPEC.md — 확인할 사실, 결정할 것, 설계 초안, 테스트
