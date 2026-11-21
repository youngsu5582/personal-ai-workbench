# SPEC: audio-generate — 글을 소리로 (TTS)

면접 준비 서비스(`deep-answer`)가 면접관의 질문(한두 문장, 300자 이내, 한국어에 영어 기술 용어가 섞임)을 소리로 만들어 재생한다.
요구는 `playground/.claude/plans/2026-11-21-interview-prep-service/workbench-needs.md` 의 A1 · A3 이고 **11/28 저녁이 기한**이다.
STT(받아 적기)는 이미 `text-generate` + 첨부로 끝났다(PR #118~#121).

## 1. 확인한 사실

문서(공식, 2026-11-21 읽음)와 실제 호출(`GeminiTtsProbeLiveTest`, 2026-11-22)을 구분한다. 호출은 `gemini-3.8-flash-tts`, 목소리 `Kore` 로 두 번이다.

| # | 사실 | 근거 |
|---|---|---|
| F1 | 3.8 TTS 는 `generateContent` 가 아니라 **Interactions API**(`POST /v1beta/interactions`). 기존 Gemini 텍스트 어댑터와 요청 · 응답 모양이 다르다 | 문서 · live |
| F2 | 요청: `input` 이 글 하나면 `{"model", "input": "글", "response_format": {"type": "audio"}, "generation_config": {"speech_config": [{"voice": "Kore"}]}}` | live 성공 |
| F3 | **말투(`style`)** 는 글 속 지시가 아니라 단계 목록으로 준다: `input: [{"type": "user_input", "content": [{"type": "text", "text": "글", "annotations": [{"type": "speech_metadata", "style": "차분하고 또렷하게"}]}]}]`. 같은 본문을 `turn_list` 모양(`role` + `content`)으로 주면 "step_list 를 쓰라"며 400 이다. 글 속의 "Say cheerfully:" 는 소리 내어 읽힌다 | live · 문서 |
| F4 | 응답: `status: "completed"`, `steps[].content[]` 중 `type: "audio"` 의 `data`(base64). **헤더 있는 WAV, 24kHz 모노 16bit**. 스트리밍이 아니면 헤더를 감쌀 필요가 없다 | live |
| F5 | **WAV 끝에 `C2PA` 청크**(콘텐츠 출처 메타데이터)가 붙는다(약 7.6KB). RIFF 크기 필드는 이를 포함한다. 그래서 **길이는 파일 크기가 아니라 `data` 청크로** 계산해야 사용량(25토큰/초)과 맞는다(8.48초 = 212토큰 ÷ 25, 파일 크기로 하면 8.64초). 이 바이트는 내용 주소 저장 규칙(ADR 0001)에 따라 고치지 않는다 | live |
| F6 | **사용량은 `generateContent` 의 `usageMetadata` 와 다른 모양**이다: `usage.total_input_tokens`(글), `usage.total_output_tokens`(오디오), `output_tokens_by_modality: [{modality: "audio", tokens}]`, `total_thought_tokens: 0`. 단가표 경로는 배열 없이 점으로 가른 맵 경로(`total_input_tokens`, `total_output_tokens`)로 충분하다 | live |
| F7 | 단가(Standard): `gemini-3.8-flash-tts` 입력 $0.50 · 출력 $9.00 /1M 토큰(2026-12-31 까지), 2027-01-01 부터 $1.00 · $18.00. `flash-lite-tts` 출력 $6.00 → $12.00. 오디오 25토큰/초 | 문서(pricing 원문) |
| F8 | 목소리 기본 30개, 한국어 지원, 언어 자동 감지. 입력 토큰 한도 8,192, 출력 16,384. 한영 혼용 기술 용어의 품질은 문서에 없다 | 문서 |
| F9 | 연속 호출에서 **429 가 한 번** 나왔다(성공 2회 뒤). 본문: "limit: 3 requests per minute on **Free Tier**". 키의 프로젝트가 Free Tier 다. 문서의 한도 표에는 TTS 가 없다 | live 본문 · 문서 |
| F11 | Interactions 의 오류 본문은 `{"error": {"message", "code": "too_many_requests"}}` 로 `code` 가 문자열이다(`generateContent` 는 숫자 + `status`) | live |
| F12 | 모델 ID 는 `gemini-3.8-flash-tts` · `gemini-3.8-flash-lite-tts`(접미사 없음). 목소리 30개 이름, 응답의 소리는 `steps[type=model_output].content[type=audio].data`, `response_format.mime_type` 으로 `audio/wav` 를 고를 수 있다 | 문서 |
| F10 | OpenAI `tts-1` · `tts-1-hd` · `gpt-4o-mini-tts` 는 2027-01-06 종료가 문서에 있다 → Gemini 만 | 문서 |

요구 문서와 달라진 것: ① "L16 이면 WAV 헤더로 감싼다"는 필요 없다(F4) ② 사용량은 새 모양이다(F6) ③ 말투는 단계 목록이다(F3).

## 2. 결정 (2026-11-22)

| # | 무엇 | 결정 |
|---|---|---|
| D1 | 요청 키 | `{"type": "audio-generate", "text": …, "voice": …, "style": …}` + 요청 바깥의 `model`. DB 에 저장되는 계약이다 |
| D2 | 결과물 메타 | 이미지 / 오디오를 `type` 으로 가르고, **판별자 없는 옛 행은 이미지**로 읽는다. 마이그레이션 없이 호환한다(`generated_files.metadata` 는 JSON) |
| D3 | 포트 | **새 포트** `ExternalApiAudioProvider`. 텍스트 · 판정 때와 같은 이유 — 모델이 서로의 일을 못 한다. 이미지 포트를 넓히지 않는다 |
| D4 | 목소리 | 어댑터가 `voices(model)` 로 밝히고 모르는 목소리는 **받는 목소리 목록을 담은 400**(`efforts` 와 같은 모양). 이름 목록의 정본은 어댑터 코드다 |
| D5 | 길이(초) | **서버가 WAV 의 `data` 청크로** 계산한다(F5). 저쪽이 말해 주는 값이 아니라 바이트에서 나오는 값이다 |
| D6 | 단가 | 입력 `total_input_tokens` · 출력 `total_output_tokens` 에 값을 매기는 행을 모델 둘 × 기간 둘(2026-12-31 경계)로 넣는다. 새 단가 경로 문법은 필요 없다 |
| D7 | 기본 모델 | `gemini-3.8-flash-tts`(품질), `…flash-lite-tts` 도 받는다. 질문 한 건의 비용 차이는 $0.002 안팎이다 |
| D8 | 조회 응답의 파일 | 공통 칸(`sha256` · `url` · `previewUrl` · `mimeType` · `fileSize`) + 종류의 사실(이미지 `width` · `height`, 오디오 `durationSeconds`). **종류에 없는 칸은 싣지 않는다**(`null` 이 아니라 칸째 없음 — `previewUrl: null` 의 "할 수 없음" 과 섞이지 않게). 받는 쪽은 `mimeType` 으로 가른다(입력 파일과 같은 기준). PR A 에서 정함 |
| D9 | 길이를 못 구하는 바이트 | WAV 가 아니거나 `data` 청크가 없으면 길이 없이 저장하지 않고 Task 실패로 남긴다(사유에 형식). PR A 의 ADR 에서 정함, 구현은 PR B |

남은 결정: 아래 §6.

## 3. 설계 초안

```
POST /api/jobs  {"option": {"type":"audio-generate","text":"…","voice":"Kore","style":"…"}, "model":"gemini-3.8-flash-tts"}
  접수(GenerationJobService) — 모델 확인 · voices(model) 확인(400 + 받는 목소리)
  실행(TaskWorker.processAudio) — ExternalApiAudioProvider.generateAudio → 바이트 + 메타(mimeType, 길이) → 보관 → 결과 행
  조회 — files[] = { sha256, url, previewUrl, mimeType, fileSize, durationSeconds }   (D8)
```

- **저장 · 다운로드는 그대로 쓴다.** 내용 주소 저장(`BlobKey`)과 다운로드(`GeneratedFileController`)는 형식에 무관하다.
- **바뀌는 곳**(코드로 확인): `FileMetadata`(가로 · 세로 양수 검증), `ExternalApiGenerateResult` · `ImageMetadata`(이미지 전용 — 새 포트는 이것을 쓰지 않는다), `TaskWorker.store`(메타 변환), `FileResponse`(형식은 `mimeType` 으로 이미 있다 — 길이를 더하고 종류에 없는 칸은 뺀다), 콘솔의 `files` 렌더링.
- **Gemini 클라이언트:** Interactions 전용 클라이언트를 새로 둔다. 실패 번역(`GeminiFailures`)은 오류 본문의 모양(`{"error": {"message", "code"}}`)이 달라 그대로 쓸 수 있는지 확인한다.
- **재시도:** 429 · 5xx 는 기존처럼 재시도 가능한 실패로 가른다(F9).

## 4. 테스트

| 테스트 | 증명하는 것 |
|---|---|
| `FileMetadata` 직렬화 | 판별자 없는 옛 행이 이미지로 읽힌다. 오디오 행이 왕복된다 |
| WAV 길이 | `data` 청크로 계산하고 꼬리 청크(`C2PA`)에 속지 않는다 — 실제 응답의 첫 64바이트와 청크 크기를 고정 |
| `GeminiAudioProviderTest` | 요청 본문의 모양(글만 / `style` 을 단계 목록으로), 응답에서 오디오와 usage 를 꺼냄, 오디오가 없는 응답은 실패 |
| `GenerationJobApiTest` | 모르는 목소리는 400 + `availableVoices`, 모르는 모델은 400 |
| `TaskWorkerTest` | 오디오 작업이 파일 한 개를 남기고 원장에 사용량이 적힌다 |
| 단가표 | `total_input_tokens` · `total_output_tokens` 로 비용이 계산되고 2027-01-01 경계가 갈린다 |
| `GeminiAudioProviderLiveTest` (`GEMINI_LIVE=1`) | 실제 응답, usage 의 모양, 한국어 질문 |

## 5. 하지 않는 것
- OpenAI TTS (F10)
- 다중 화자 · 사용자 정의 목소리 · 스트리밍 재생
- 오디오의 변환 · 정규화(샘플레이트 · 코덱) — 받은 WAV 를 그대로 둔다(ADR 0001)
- STT 와의 결합(받아 적기 → 다음 질문 → 읽기를 한 작업으로) — 서비스가 작업 둘로 부른다

## 6. 남은 결정 · 확인
- 한국어 질문(한영 혼용)의 음질을 **사람이 듣고** 판단한다. `Kore` 외 목소리와 `flash-lite-tts` 도 같은 문장으로 비교한다.
- F9 의 429: 한도를 문서에서 찾거나(이 키의 프로젝트가 Free Tier 인지 포함) 재시도 간격을 정한다. 서비스는 턴마다 한 건이라 순차이지만, 세션 뒤 판정과 겹치면 부딪힐 수 있다.
- `style` 의 문장은 어느 언어가 좋은지(영어 `cheerful` vs 한국어 지시) 실측한다.
