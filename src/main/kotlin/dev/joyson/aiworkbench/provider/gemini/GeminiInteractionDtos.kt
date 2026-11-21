package dev.joyson.aiworkbench.provider.gemini

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Gemini Interactions API(`POST /v1beta/interactions`)의 요청 · 응답을 그대로 옮긴 타입이다.
 *
 * `generateContent` 와 모양이 다르다 — 모델이 경로가 아니라 본문에 있고, 칸 이름이 snake_case 이며,
 * 입력과 출력이 `steps` 의 단계 목록이다. 이 파일은 **우리 도메인을 모르고**, 우리가 보내는 것만 선언한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeminiInteractionRequest(
    val model: String,
    /** 단계 목록이다. 사람의 차례는 `user_input` 단계 하나다. */
    val input: List<GeminiStep>,
    @param:JsonProperty("response_format")
    @get:JsonProperty("response_format")
    val responseFormat: GeminiResponseFormat,
    @param:JsonProperty("generation_config")
    @get:JsonProperty("generation_config")
    val generationConfig: GeminiInteractionGenerationConfig? = null,
)

/** 요청과 응답이 같은 모양을 쓴다. 요청은 `user_input`, 응답의 답은 `model_output` 단계다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiStep(
    val type: String? = null,
    val content: List<GeminiStepContent>? = null,
)

/**
 * 단계 안의 내용 하나. 요청은 `text`, 응답의 소리는 `audio` 다.
 *
 * 말투는 글 속의 지시가 아니라 [annotations] 로 준다 — 글 속에 쓰면 그 지시까지 소리 내어 읽힌다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiStepContent(
    val type: String? = null,
    val text: String? = null,
    /** 응답에서만 온다. 소리의 base64. */
    val data: String? = null,
    /** 요청에서만 보낸다. */
    val annotations: List<GeminiSpeechAnnotation>? = null,
)

data class GeminiSpeechAnnotation(val type: String, val style: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeminiResponseFormat(
    val type: String,
    @param:JsonProperty("mime_type")
    @get:JsonProperty("mime_type")
    val mimeType: String? = null,
)

data class GeminiInteractionGenerationConfig(
    /** 화자가 하나면 목록에 목소리 하나다. 여럿이면 모양이 객체(`speakers`)로 바뀌지만 보내지 않는다. */
    @param:JsonProperty("speech_config")
    @get:JsonProperty("speech_config")
    val speechConfig: List<GeminiSpeechConfig>,
)

data class GeminiSpeechConfig(val voice: String)

/**
 * 응답. 시각 · 서비스 등급 같은 칸이 섞여 오지만 쓰지 않아 버린다.
 * 과금 근거는 [usage] 에 원문 그대로 남는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiInteractionResponse(
    /** 청구서 · 지원 문의와 대조할 때 쓴다. */
    val id: String? = null,
    /** 끝까지 됐으면 `completed` 다. */
    val status: String? = null,
    /**
     * 이 호출이 쓴 토큰. `generateContent` 의 `usageMetadata` 와 칸 이름이 다르다
     * (`total_input_tokens` · `total_output_tokens` · `output_tokens_by_modality` …). 해석하지 않고 통째로 받는다.
     */
    val usage: Map<String, Any?>? = null,
    val steps: List<GeminiStep>? = null,
)
