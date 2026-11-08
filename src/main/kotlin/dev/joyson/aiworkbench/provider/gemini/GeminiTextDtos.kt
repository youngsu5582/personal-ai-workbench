package dev.joyson.aiworkbench.provider.gemini

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonRawValue

/**
 * Gemini `generateContent` 의 요청 · 응답을 그대로 옮긴 타입이다.
 *
 * 이 파일은 **우리 도메인을 모른다.** 이 API 는 도구 · 이미지 입력 · 여러 후보까지 받지만, 여기에는 우리가 보내는 것만 선언한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    /** system 프롬프트 자리다. 글만 받는다. */
    val systemInstruction: GeminiContent? = null,
    /** 없으면 형식 없는 글 · 모델 기본 thinking 이다. */
    val generationConfig: GeminiGenerationConfig? = null,
)

/** 요청과 응답이 같은 모양을 쓴다. [role] 은 요청의 `contents` 에서만 보낸다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiContent(
    val parts: List<GeminiPart>? = null,
    val role: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiPart(
    val text: String? = null,
    /** 응답에서만 온다. true 면 생각의 요약이지 답이 아니다. */
    val thought: Boolean? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeminiGenerationConfig(
    /** 스키마를 줄 때만 `application/json` 이다. */
    val responseMimeType: String? = null,
    /**
     * 스키마 원문. 문자열을 **그대로 박는다**(`@JsonRawValue`) — 키 순서가 출력 키 순서라서다.
     * `responseSchema` 가 아니라 이쪽인 이유는 JSON Schema 를 그대로 받는 칸이라서다.
     */
    @get:JsonRawValue
    val responseJsonSchema: String? = null,
    val thinkingConfig: GeminiThinkingConfig? = null,
)

/** `MINIMAL` · `LOW` · `MEDIUM` · `HIGH`. */
data class GeminiThinkingConfig(val thinkingLevel: String)

/**
 * 응답. 안전 평가 · 인용 · 생각 서명 같은 칸이 섞여 오지만 쓰지 않아 버린다.
 * 과금 근거는 [usageMetadata] 에 원문 그대로 남는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiGenerateContentResponse(
    /** 프롬프트가 막히면 비어 온다. */
    val candidates: List<GeminiCandidate>? = null,
    val promptFeedback: GeminiPromptFeedback? = null,
    /**
     * 이 호출이 쓴 토큰. OpenAI 쪽과 같은 이유로 **Map 으로 통째로** 받는다.
     * 0 인 칸은 저쪽이 빼고 보낸다 — 생각하지 않은 호출엔 `thoughtsTokenCount` 가 없다.
     */
    val usageMetadata: Map<String, Any?>? = null,
    /** 청구서 · 지원 문의와 대조할 때 쓴다. */
    val responseId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiCandidate(
    val content: GeminiContent? = null,
    /** `STOP` · `MAX_TOKENS` · `SAFETY` · `RECITATION` … */
    val finishReason: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiPromptFeedback(
    /** 있으면 프롬프트가 막혀 후보가 없다. `SAFETY` · `BLOCKLIST` · `PROHIBITED_CONTENT` … */
    val blockReason: String? = null,
)
