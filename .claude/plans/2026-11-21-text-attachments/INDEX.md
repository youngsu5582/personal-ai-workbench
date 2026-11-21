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
- [ ] 업로드를 오디오까지 넓힌다
- [ ] `TextGenerateOption.attachments` · 접수 검증 · 워커의 입력 경로
- [ ] 포트 · Gemini 어댑터의 파일 파트 (OpenAI 는 D4 에 따라 이미지만)
- [ ] live · 로컬 스모크 · development.md · scripts

## Resume Point
값은 닫혔다(SPEC D2 · D4). "업로드를 오디오까지 넓힌다" 부터 구현한다 — `ImageContentType` 을 일반 형식 판별로 넓히고 한도를 15MB 로.
DTO 의 `GeminiPart.inlineData` · `fileData` 와 live 테스트 2건은 이미 있다. 어댑터가 파일을 실어 보내는 부분은 아직 없다.
서명 주소(보관소 presigned)를 Gemini 가 읽는지는 보관소가 인터넷에서 닿는 환경에서만 확인된다 — 안 읽으면 Gemini 는 늘 바이트로 보낸다(D4).
live 호출(`GEMINI_LIVE=1`)은 사용자가 승인했다(2026-11-21). `.env` 는 이 워크트리에 있다 — Claude 는 읽지 못하므로 live 는 테스트 코드가 읽게 한다.

## 남은 결정 (뒤로 미룸)
- 콘솔의 글 생성 화면에서 파일을 고르는 칸 — 백엔드가 먼저
- 템플릿이 첨부를 실행 입력으로 받게 할지

## Files
- SPEC.md — 확인할 사실, 결정할 것, 설계 초안, 테스트
