package dev.joyson.aiworkbench.provider.openai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonRawValue

/**
 * OpenAI Responses API(`/v1/responses`) 의 요청·응답을 그대로 옮긴 타입이다.
 *
 * 이 파일은 **우리 도메인을 모른다.** 이미지 DTO 와 같은 자리다 — 옮기는 일은 어댑터가 한다.
 * 이 API 는 도구 호출·이어 쓰기·이미지 입력까지 받지만, 여기에는 우리가 보내는 것만 선언한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpenAIResponseRequest(
    val model: String,
    /** 문자열 하나면 user 메시지 하나로 본다. */
    val input: String,
    /** system 메시지 자리다. */
    val instructions: String? = null,
    /** 없으면 형식 없는 글이다. */
    val text: OpenAITextOptions? = null,
    /**
     * 저쪽에 응답을 남기지 않는다.
     *
     * 남기는 것이 기본값인데, 우리는 응답을 다시 꺼내 읽지도 이어 쓰지도 않는다.
     * 남겨 둘 이유 없이 사용자의 글을 남의 보관소에 두지 않는다.
     */
    val store: Boolean = false,
)

data class OpenAITextOptions(val format: OpenAIJsonSchemaFormat)

/** `text.format` 의 JSON Schema 변형. */
class OpenAIJsonSchemaFormat(
    val name: String,
    /**
     * 스키마 원문. 문자열을 **그대로 박는다**(`@JsonRawValue`).
     *
     * 객체로 다시 읽어 직렬화하면 그 사이 키 순서가 지켜진다는 보장이 필요하고, 키 순서가 곧 출력 키 순서다.
     */
    @get:JsonRawValue
    val schema: String,
    /** 스키마를 반드시 지키게 한다. 끄면 따르려고 애쓸 뿐 어겨도 200 이다. */
    val strict: Boolean = true,
) {
    val type: String = "json_schema"
}

/**
 * 응답.
 *
 * 이미지 응답처럼 모르는 필드를 모으지 않는다(`ignoreUnknown`). 이 API 는 **보낸 파라미터를 최상위에
 * 되돌려 준다**(temperature · tools · truncation …) — 모으면 미지 필드 경고가 그 메아리로 덮인다.
 * 단가의 축은 [usage] 에 원문 그대로 남으니, 버려서 잃는 과금 근거는 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAIResponse(
    /** `resp_...`. 청구서·지원 문의와 대조할 때 쓴다. */
    val id: String? = null,
    /** `completed` · `incomplete` · `failed` … */
    val status: String? = null,
    @param:JsonProperty("incomplete_details")
    @get:JsonProperty("incomplete_details")
    val incompleteDetails: OpenAIIncompleteDetails? = null,
    val error: OpenAIError? = null,
    /** 메시지 말고도 추론 요약 같은 항목이 섞여 온다. 글은 `message` 항목 안에 있다. */
    val output: List<OpenAIOutputItem>? = null,
    /**
     * 이 호출이 쓴 토큰. 이미지 응답과 같은 이유로 **Map 으로 통째로** 받는다 — 필드를 하나씩 선언하면
     * 저쪽이 항목을 늘렸을 때 그것이 말없이 사라지고, 그 값이 과금 근거다.
     */
    val usage: Map<String, Any?>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAIIncompleteDetails(
    /** `max_output_tokens` · `content_filter` */
    val reason: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAIOutputItem(
    /** `message` · `reasoning` … */
    val type: String,
    val content: List<OpenAIOutputContent>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAIOutputContent(
    /** `output_text` 면 [text] 가, `refusal` 이면 [refusal] 이 온다. */
    val type: String,
    val text: String? = null,
    val refusal: String? = null,
)
