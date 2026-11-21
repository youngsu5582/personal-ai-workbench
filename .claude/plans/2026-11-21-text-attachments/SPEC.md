# SPEC: text-generate 에 이미지 · 오디오를 함께 보낸다 (초안)

`TextGenerateOption` 의 KDoc 이 이미 방향을 정해 두었다 — "입력으로 가르면 `text-to-text` · `image-to-text` 처럼 곱으로 늘어나는데,
무엇을 만드는지는 글 하나로 같다. 입력이 늘면 이 타입에 필드로 더한다." 그래서 새 종류를 만들지 않고 필드를 더한다.

이미 있는 것:
- `FileSource`(`generated` · `uploaded`) 와 `GenerationOption.fileSources()` — 지금 `TextGenerateOption` 만 빈 목록이다
- `FileSourceResolver` — 소유 확인(접수) · 바이트 또는 읽기 주소로 풀기(실행)
- `TaskWorker.callWithInputs` — 주소로 먼저 보내고, 저쪽이 못 받아가면(`INPUT_UNREACHABLE`) 바이트로 다시 보낸다. 판정 · I2I 가 쓴다
- `ExternalApiFileInput`(`Bytes` · `Url`) — 판정 포트가 이미 쓴다
- Gemini 어댑터(#114) — `generateContent` 의 `parts` 에 파일 파트를 더하면 된다

## 1. 확인할 사실 (결정 전에)

| # | 무엇 | 왜 |
|---|---|---|
| F1 | Gemini 가 `inlineData` 로 받는 오디오 · 이미지 형식과 요청 크기 한도 | 업로드 허용 형식과 크기(D2)를 여기 맞춘다 |
| F2 | Gemini `fileData.fileUri` 가 임의의 https 서명 주소를 받는가 (File API · GCS 만인가) | 못 받으면 Gemini 는 늘 바이트로 보내야 한다 — 주소 먼저 보내는 흐름을 탈지(D4) |
| F3 | 오디오 · 이미지 입력 토큰 단가가 텍스트 입력과 다른가 (모델별) | 다르면 입력 종류별 단가 경로(D3)가 필요하다 |
| F4 | 오디오가 섞인 실제 `usageMetadata` 모양 — `promptTokensDetails` 의 AUDIO 항목, 0 이면 항목이 빠지는가 | D3 의 경로 표현과 `zeroIfAbsent` 적용을 정한다 |
| F5 | OpenAI Responses API 가 텍스트 모델에 오디오 입력을 받는가 (이미지는 판정에서 보냄) | OpenAI 어댑터가 무엇을 받는다고 답할지(D4) |

### 확인 결과 (2026-11-21)

| # | 답 | 근거 |
|---|---|---|
| F1 | 인라인 오디오: wav · mp3 · mpeg · aiff · aac · ogg · flac · m4a · l16 · opus · alaw · mulaw · webm. 이미지: png · jpeg · webp · heic · heif. **요청 전체**(글 + 모든 파일)가 20MB 까지, 넘으면 Files API. 오디오는 프롬프트당 9.5시간 | 공식 문서 `aistudio.google.com/docs/audio` 원문 · 이미지 형식은 요약본이라 **미검증** |
| F2 | 공개 https 주소(`googlelogo_color_272x92dp.png`)를 `fileData.fileUri` 로 보내니 **받았다**(`GeminiTextProviderLiveTest`). 서명된 주소 · 인터넷에서 못 닿는 보관소 주소는 **미검증** | live. 문서는 File API URI 만 규칙으로 적는다 |
| F3 | 우리 모델 둘은 텍스트 · 이미지 · 오디오 입력 단가가 **같다**(3.6 Flash 0.75→1.50, 3.5 Flash-Lite 0.30). 다른 모델(2.5 Flash · 3 Flash · 3.1 Flash-Lite)은 오디오가 다르지만 우리 목록에 없다 | 문서 가격표 — 두 모델 행은 원문 |
| F4 | 입력 종류별 값은 **배열**이다: `promptTokensDetails: [{modality: AUDIO, tokenCount: 50}, {modality: TEXT, tokenCount: 9}]`. 0 인 종류는 항목이 빠진다(텍스트만 보내면 TEXT 하나). **`promptTokenCount`(59)는 모든 종류의 합**이다. 2초 WAV 가 50 토큰 — 문서의 초당 32 와 어긋난다 | live |
| F5 | Responses API 의 `input` 파트는 `input_text` · `input_image` · `input_file` 뿐, 오디오가 없다. 오디오 입력은 Chat Completions 의 audio 모델(`gpt-audio-*`) 경로다 | 공식 문서 — 같은 페이지 안에 "audio" 를 말하는 문장이 하나 있으나 열거된 파트에는 없다 |

**D3 을 하지 않는다.** F3 · F4 로 현재 단가표(`promptTokenCount` 한 칸)가 이미 오디오 · 이미지 입력을 맞게 매긴다.
배열 경로 · `PriceRates` 확장 · ADR 0002 절이 모두 빠진다. 입력 단가가 다른 모델을 목록에 더하는 날 다시 연다.

## 2. 결정 (2026-11-21, D5 는 기본안)

| # | 무엇 | 결정 | 남은 것 |
|---|---|---|---|
| D1 | 요청 모양 | **보관소 파일만, 파일 목록.** `attachments: List<FileSource> = emptyList()` — I2I 의 `sources` 와 같은 참조. 바깥 파일은 부르는 쪽이 `POST /api/uploads` 로 먼저 올리고, 처음 참조될 때 형식 · 크기를 검증받은 뒤 쓴다. 이미지인지 오디오인지는 업로드 모듈이 알아 요청에 적지 않는다. 빈 목록이 기본이라 옛 행 · 옛 요청이 그대로 읽힌다. 순서가 곧 모델이 읽는 순서. **상한은 1개**(`MAX_ATTACHMENTS`) — `ImageOption.sources` 와 같은 이유로 목록의 모양만 먼저 고정한다. 올린 파일 하나가 Provider 의 요청 한도(20MB)의 대부분이라 늘리려면 합계를 따져야 한다 | 외부 URL 을 직접 받는 것은 하지 않는다 — 형식 · 크기 검증이 없고 SSRF 방어가 새로 필요하다 |
| D2 | 업로드를 오디오까지 | **크기 한도는 형식과 상관없이 하나, 15MB**(2026-11-21). Gemini 의 20MB 는 글 · 지시문까지 합친 요청 전체이고 base64 가 약 33% 늘리므로 원본은 15MB 아래여야 안에 든다. **허용 오디오는 wav · mp3 · ogg · flac · m4a · aac** — 매직넘버를 형식마다 검증해야 해서 흔한 여섯만 두고, 필요해지면 하나씩 더한다. 형식 판별은 `ImageContentType` 을 일반 형식 판별로 넓힌다(오디오 매직넘버). 이미지 작업(I2I · 판정)에 오디오 업로드를 넘기면 접수 400 | 허용 오디오 형식 목록 (F1) |
| D3 | 입력 종류별 단가 | **하지 않는다 (확인 결과 참고).** ~~단가표 경로를 배열까지.~~ 아래는 하지 않기로 한 안이다 — `PriceRates` 경로가 배열의 "modality 가 AUDIO 인 항목의 tokenCount" 를 가리킬 수 있게 한다. usage_raw 는 원문 그대로, 계산 코드는 Provider 를 모른다. 텍스트 · 이미지 · 오디오 입력에 각자 단가. `promptTokenCount` 한 칸에 단가를 매기던 V20 표는 그대로 두고 새 기간의 표로 바꾼다. **ADR 0002 에 절**로 남긴다 | F3 이 "단가가 같다" 면 이 결정은 필요 없다. 경로 문법(예: `promptTokensDetails[modality=AUDIO].tokenCount`)과, 항목이 빠진 것을 `zeroIfAbsent` 로 볼지 |
| D4 | 어댑터가 받는 입력을 밝힌다 | **OpenAI 텍스트 어댑터는 이미지 MIME 만 답한다**(2026-11-21) — Responses API 에 오디오 파트가 없다(F5). 오디오를 보내면 접수 400. **MIME 집합.** `efforts(model)` 과 같은 자리 — 포트에 `fun inputTypes(model: String): Set<String>`. **기준은 저쪽 API 가 받는 것이 아니라 어댑터가 실제로 싣는 것**이다 — 싣지 못하는 형식을 밝히면 접수된 첨부가 조용히 빠진다. 그래서 파일 파트를 싣기 전까지 어댑터는 빈 집합을 답하고, 싣는 PR 에서 채운다. 업로드 모듈도 MIME 으로 판정하므로 그대로 비교되고, 접수가 첨부의 형식을 보고 400 | 이름. 주소 대신 바이트를 늘 원하는 어댑터면 F2 에 따라 처리 |
| D5 | 원장에 무엇을 적나 | `TextRequest` 에 첨부 수(`DecisionRequest.images` 처럼). 비용 차이는 usage 원문이 설명한다 | 형식별 수까지 적을지 |

## 3. 설계 초안

```
요청 option.attachments ─▶ 접수 (GenerationJobService)
                             · 소유 확인 — FileSourceResolver.isUsable (지금처럼)
                             · 어댑터가 그 형식을 받나 — provider.inputTypes(model)   (D4)
                          ─▶ 실행 (TaskWorker.processText)
                             · callWithInputs 로 감싼다 — 판정과 같은 길
                             · ProviderRequestMapper.from(option, files) → ExternalApiTextRequest.files
                          ─▶ GeminiTextProvider: parts = [text, inlineData…]    OpenAITextProvider: input_image…
```

- `GenerationOption.fileSources()` 의 `TextGenerateOption -> emptyList()` 가 `attachments` 가 된다. 조회 화면의 입력 파일 미리보기도 같이 따라온다.
- 업로드 모듈의 공개 값(`UploadedFiles` 의 `UsableUpload.mimeType`)이 형식을 말한다. 엔티티는 경계를 넘지 않는다.

## 4. 테스트 (초안)

| 테스트 | 증명하는 것 |
|---|---|
| `TextGenerateOption` 직렬화 | `attachments` 없는 옛 행이 그대로 읽힌다 |
| `GenerationJobApiTest` | 남의 파일 · 받지 않는 형식은 접수 400 |
| `TaskWorkerTest` | 첨부가 있으면 파일이 포트까지 가고, 주소를 못 받아가면 바이트로 다시 보낸다 |
| `GeminiTextProviderTest` | 파일 파트의 모양 · 순서 |
| `UploadSpec` · 형식 판별 | 오디오 매직넘버, RIFF 가 WAV 와 WebP 를 가른다 |
| `GeminiTextProviderLiveTest` (`GEMINI_LIVE=1`) | 오디오 · 이미지 첨부의 실제 응답과 usage 모양 |

## 5. 하지 않는 것

- 자막 · 타임스탬프가 붙는 전사 — 결과 모양과 과금 단위가 달라 필요해지면 별도 종류로 본다
- 긴 오디오를 나눠 보내기 — 한도를 넘으면 접수에서 거절하고 나누는 일은 부르는 쪽이 한다
- 올라온 오디오의 변환 · 정규화(형식 · 샘플레이트 · 음량) — 검증(매직넘버 · 크기)만 한다.
  보관소는 sha256 내용 주소라(ADR 0001) 바이트를 바꾸면 부르는 쪽이 선언한 해시와 어긋나고, 변환에는 인코더 의존성이 든다.
  Provider 가 받지 않는 형식은 바꿔 맞추지 않고 D4 로 접수에서 400 이다
- 콘솔의 파일 선택 칸 · 템플릿의 첨부 입력 (INDEX 남은 결정)
- TTS — 다음 plan. 새 ADR(0006) 하나로 "생성 결과물이 이미지 밖으로 넓어진다" 를 정한다 — 저장 컨테이너 · 코덱, `FileMetadata` 를 이미지 · 오디오로 가르는 표기(판별자 없는 옛 행은 이미지), 조회 · 다운로드의 형식. raw PCM 이 오면 WAV 헤더로 감싸는 것(재인코딩 아님)과 코덱 통일 여부도 거기서
