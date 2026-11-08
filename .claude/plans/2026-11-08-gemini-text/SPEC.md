# SPEC: Gemini 텍스트 모델로 text-generate 를 받는다

`text-generate` 는 지금 OpenAI 텍스트 모델만 받는다. 같은 요청(`POST /api/jobs`, `model` 만 다름)으로
Gemini Flash · Flash-Lite 를 부를 수 있게 한다. 옵션 · 결과 · 원장 · 비용 경로는 그대로 다시 쓴다.

## 1. 확인한 사실 (2026-11-08, 공식 문서)

| 모델 ID | 상태 | Free Tier | 유료 입력 / 출력 (1M, USD) | 기본 thinking | 종료 예정 |
|---|---|---|---|---|---|
| `gemini-3-flash-preview` ("Gemini 3 Flash") | Preview | 무료 | 0.50 / 3.00 | high | 미정 · 대체 권고 `gemini-3.6-flash` |
| `gemini-3.6-flash` | Stable | 무료 | 0.75 / 3.75 (2027-01-01 부터 1.50 / 7.50) | medium | 미정 |
| `gemini-3.8-flash` | New | 무료 | 3.6 과 같음 | — | 미정 |
| `gemini-3.1-flash-lite` | Stable | 무료 | 0.25 / 1.50 | 문서에 없음 | 2027-05-07 · 대체 `gemini-3.5-flash-lite` |
| `gemini-3.5-flash-lite` | Stable | 무료 | 0.30 / 2.50 | minimal | 미정 |

- **"Gemini 3 Flash-Lite" 라는 모델은 없다.** Flash-Lite 는 3.1 · 3.5 두 세대다.
- 출력 단가에 thinking 토큰이 포함된다 ("Output price (including thinking tokens)").
- Free Tier 는 **결제 계정이 연결되지 않은 프로젝트**에만 적용된다. 결제를 연결하면 Tier 1 이 되고 전부 유료다.
- Free Tier 의 입력 · 출력은 **Google 제품 개선에 쓰인다** ("Used to improve our products: Yes").
- Free Tier 한도(RPM · RPD)는 문서에 숫자가 없다. AI Studio 대시보드에서만 보인다.
- 다섯 모델 모두 입력 1,048,576 · 출력 65,536 토큰, Structured outputs · Thinking 지원.

출처: [Pricing](https://aistudio.google.com/docs/pricing) · [Models](https://aistudio.google.com/docs/models) ·
[Deprecations](https://aistudio.google.com/docs/deprecations) · [Rate limits](https://aistudio.google.com/docs/rate-limits)

## 2. 결정 (2026-11-08)

| # | 무엇 | 결정 |
|---|---|---|
| D1 | 넣을 모델 | `gemini-3.6-flash` + `gemini-3.5-flash-lite` (둘 다 Stable · 종료일 없음) |
| D2 | 단가표 단가 | **유료 단가**. 이 서비스 관점에선 토큰이 나간 것이 맞다. Free Tier 를 어떻게 볼지는 뒤에 (대사 · 추적과 함께) |
| D3 | `effort` 눈금 | `minimal` 을 더한다. **어댑터마다 저쪽 API 가 표현할 수 있는 effort 를 밝히고, 접수가 그것으로 거른다** (400) |

D3 의 "밝힌다" 는 **API 의 어휘**까지다. 모델마다 받는 값이 다른 것(어떤 OpenAI 모델은 `none` 을 거절)은 여전히
허용표 없이 저쪽 거절을 Task 실패로 남긴다 — 모델별 표는 저쪽이 바꿀 때마다 어긋난다. API 어휘는 바뀌지 않는 사실이다.

## 3. 설계

### 3.1 API — `generateContent` 를 쓴다

`POST /v1beta/models/{model}:generateContent`, 헤더 `x-goog-api-key`.

| | generateContent (채택) | Interactions API |
|---|---|---|
| 문서상 위치 | legacy, "remains fully supported" | 새 프로젝트 권장 |
| 멈춘 이유 | `finishReason` (STOP · MAX_TOKENS · SAFETY …) · `promptFeedback.blockReason` | `status: incomplete` 뿐, 이유 칸이 문서에 없다 |
| 저장 | 저장하지 않는다 | 기본 저장 → `store: false` 를 꼭 보내야 한다 |
| 버전 헤더 | 없음 | 예제마다 `Api-Revision` 이 붙는데 필수인지 문서에 없다 |

포트의 `StopReason` 을 채울 근거가 generateContent 에만 있다. 바꿀 일이 생기면 `GeminiTextClient` · DTO 안에서 끝난다.

### 3.2 파일

```
provider/gemini/
  GeminiProperties.kt     workbench.provider.gemini.{api-key, base-url, read-timeout, connect-timeout}
  GeminiClientConfig.kt   api-key 가 비어 있으면 빈을 만들지 않는다 (OpenAIClientConfig 와 같은 조건)
  GeminiTextClient.kt     generateContent 전송 · 실패 본문 해석
  GeminiTextDtos.kt
  GeminiTextModel.kt      D1 의 모델
  GeminiTextProvider.kt   ExternalApiTextProvider 구현
  GeminiFailures.kt       실패 → ExternalApiException
db/migration/V19__gemini_text_prices.sql
```

- `ProviderConfig` · `ProviderRegistry` 는 고치지 않는다 — 빈이 생기면 `findText` · `/api/models` 에 저절로 나온다.
- 실패 번역은 OpenAI 것과 합치지 않는다. 오류 본문 모양(`{"error":{"code","message","status"}}`)이 다르고,
  같아지는 부분(연결 실패 · 모르는 예외)은 Provider 가 셋이 될 때 합친다.

### 3.3 요청 번역

| 포트 | generateContent |
|---|---|
| `prompt` | `contents: [{role: "user", parts: [{text}]}]` |
| `instructions` | `systemInstruction: {parts: [{text}]}` |
| `jsonSchema` | `generationConfig.responseMimeType: "application/json"` + `responseJsonSchema` (원문 그대로 `@JsonRawValue`) |
| `effort` | `generationConfig.thinkingConfig.thinkingLevel`: MINIMAL · LOW · MEDIUM · HIGH. 어댑터의 `efforts` 가 이 넷이라 `none` · `xhigh` · `max` 는 접수에서 400. 어댑터도 부르기 전에 다시 보고 `REJECTED` |

### 3.4 응답 번역

| 응답 | `StopReason` / 실패 |
|---|---|
| `promptFeedback.blockReason` 있음 (후보 없음) | `CONTENT_FILTER`, 글은 빈 문자열 |
| `finishReason: STOP` + 글 있음 | `COMPLETED` |
| `finishReason: STOP` + 글 없음 | `PROVIDER_ERROR` (OpenAI 의 "글 없는 200" 과 같이) |
| `MAX_TOKENS` | `MAX_TOKENS` |
| `SAFETY` · `PROHIBITED_CONTENT` · `BLOCKLIST` · `SPII` · `RECITATION` | `CONTENT_FILTER` |
| 그 밖의 `finishReason` | `INCOMPLETE` + WARN 로그 |
| HTTP 429 | `THROTTLED` (Free Tier 일일 한도도 429 라 재시도 상한까지 실패하고 사유가 남는다) |
| 5xx · 4xx · 연결 실패 | OpenAI 와 같은 갈래 |

- 글은 `candidates[0].content.parts[]` 에서 `thought: true` 가 아닌 `text` 만 잇는다.
- `REFUSED` 는 나오지 않는다 — Gemini 에 거절 전용 칸이 없다.
- `usage` 는 `usageMetadata` 원문, `providerId` 는 `responseId`.

### 3.5 단가표 (V19)

- provider `gemini`, `effective_from` 은 단가를 정한 2026-11-08.
- 경로: `promptTokenCount` 에 입력 단가, `candidatesTokenCount` · `thoughtsTokenCount` 에 출력 단가 (출력 단가에 thinking 포함).
- **0 이면 빠지는 칸.** Google API 의 JSON 은 0 인 정수 칸을 빼고 보낸다 — 생각하지 않은 호출엔 `thoughtsTokenCount` 가,
  차단된 호출엔 `candidatesTokenCount` 가 없다. 지금 규칙(경로 하나라도 없으면 "모름")이면 `minimal` 이 기본인 flash-lite 의
  호출 상당수가 비용을 모르게 된다.
  - `PriceRates` 에 `zeroIfAbsent`(없으면 0 으로 보는 경로)를 더한다. 표가 고르는 것이라 데이터에 남고, 그 밖의 경로는 지금처럼 엄격하다.
  - 어댑터가 0 을 채워 넣지 않는다 — `usage_raw` 는 원문이어야 나중에 청구서와 대사할 수 있다.
  - 불변식: `zeroIfAbsent` 는 단가가 있는 경로여야 하고, **적어도 한 경로는 엄격해야** 한다(다 빠져도 0 이면 빈 사용량이 0 달러가 된다).
- 이미 발표된 가격 변경(`gemini-3.6-flash` 2027-01-01 인상)은 **처음부터 두 줄로 넣는다** —
  `[2026-11-08, 2027-01-01)` 옛 단가 · `[2027-01-01, NULL)` 새 단가. 비용은 호출 시각(`calledAt`)으로 표를 고르므로 그날 할 일이 없다.
- 암묵 캐시(`cachedContentTokenCount`, `promptTokenCount` 의 일부)는 할인하지 않는다 — V13 과 같이 실제보다 비싸게 잡힌다.
- `PriceBookSeedTest` 에 "Gemini 모델은 모두 단가표가 있다" · 2027-01-01 전후 단가 · 빠진 칸 계산을 더한다.

### 3.6 설정 · 문서

- `application.yaml` · `.env.example` · `deploy/overlays/home/runtime.env.example` 에 `WORKBENCH_PROVIDER_GEMINI_API_KEY`
- 공용 이름 `GEMINI_API_KEY` 는 읽지 않는다 (과금 테스트 규칙과 같은 이유)
- `docs/development.md` 키 발급(결제 미연결 프로젝트) · `docs/roadmap.md` 걸어온 길 · `scripts/text.sh` 예시

## 4. 테스트

| 테스트 | 증명하는 것 |
|---|---|
| `GeminiTextProviderTest` (MockRestServiceServer) | 경로 · 헤더 · body 모양, §3.4 표의 각 줄, thought 파트 제외, effort 거절은 호출 0회 |
| `GeminiFailuresTest` | 429 · 5xx · 4xx · 연결 실패 · 모르는 예외 |
| `PriceBookSeedTest` | Gemini 모델마다 표가 있고 응답 모양의 usage 로 계산된다 |
| `GeminiTextProviderLiveTest` (`GEMINI_LIVE=1` + 전용 키) | 글 · 스키마 키 순서 · effort low · **usageMetadata 의 실제 칸**(0 인 칸이 빠지는지) |

## 5. 하지 않는 것

- 이미지 입력(image-to-text), 이미지 생성(Nano Banana), decision 종류
- Interactions API · 스트리밍 · 컨텍스트 캐시
- Free Tier 한도를 우리 쪽에서 세거나 막는 것 — 429 로 실패하고 사유를 남긴다
- 모델 이름이 Provider 끼리 겹치는 경우 — 지금은 겹치지 않는다

## 6. 다시 볼 조건

- Free Tier 키로 부른 호출을 0 달러로 볼지 → 대사 · 추적과 함께 정한다
- Google 이 새 가격 변경을 발표한다 → 알려 주는 장치가 없다. 발표를 본 사람이 표를 닫고 새 표를 더하는 마이그레이션을 쓴다
- 응답에 `finishReason` 이 없거나 우리가 모르는 값이 자주 온다 → INCOMPLETE WARN 로그로 본다
