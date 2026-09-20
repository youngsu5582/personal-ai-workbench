package dev.joyson.aiworkbench.provider.openai

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import dev.joyson.aiworkbench.provider.UnknownFields

/**
 * OpenAI Images API 의 요청·응답을 그대로 옮긴 타입이다.
 *
 * 이 파일은 **우리 도메인을 모른다.** OpenAI 가 뭘 받고 뭘 주는지만 안다.
 * 도메인 어휘(aspectRatio·quality 등)를 OpenAI 표현으로 바꾸는 일은 adapter 가 한다.
 * 그래서 나중에 이 폴더의 전송 부분만 별도 모듈로 떼어낼 수 있다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpenAIImageRequest(
    val model: String,
    val prompt: String,
    /** 한 번의 호출로 만들 이미지 수. 이게 있어서 4장 요청이 호출 1번으로 끝난다. */
    val n: Int? = null,
    /** `1024x1024` · `1536x1024` · `1024x1536` 또는 커스텀 `WIDTHxHEIGHT` */
    val size: String? = null,
    /** `low` · `medium` · `high` · `xhigh` · `max` · `auto` */
    val quality: String? = null,
    /** `transparent` · `opaque` · `auto` */
    val background: String? = null,
    @param:JsonProperty("output_format")
    @get:JsonProperty("output_format")
    val outputFormat: String? = null,
)

data class OpenAIImageResponse(
    val created: Long = 0,
    val data: List<OpenAIGeneratedImage> = emptyList(),
    /**
     * 이 호출이 쓴 토큰. `total_tokens` 과 `input_tokens` · `output_tokens`,
     * 그리고 `input_tokens_details` 안의 세부가 온다.
     *
     * 타입으로 받지 않고 **Map 으로 통째로** 받는다. 필드를 하나씩 선언하면 OpenAI 가 항목을
     * 늘렸을 때 그것이 말없이 사라지는데, 사라진 줄도 모르는 채로 그 값이 과금 근거다.
     * 여기서 잃으면 되돌릴 방법이 없다 — 응답은 한 번뿐이다.
     *
     * 그래서 이 필드에는 **OpenAI 의 필드명이 그대로** 들어 있다. 우리 포맷이 아니다.
     */
    val usage: Map<String, Any?>? = null,
) {
    /**
     * 위에 선언하지 않은 최상위 필드가 여기 담긴다. **그 레벨에서 매핑되지 않은 것만** 온다 —
     * `created`·`data`·`usage` 는 선언돼 있으니 절대 여기 오지 않고,
     * 이미지 바이트는 `data` 안이라 구조적으로 섞일 수 없다.
     *
     * 버리더라도 **버렸다는 사실은 알아야** 한다. OpenAI 가 실제로 만든 크기·품질을 응답에 돌려준다면
     * 그건 우리가 보낸 값보다 정확한 과금 근거인데, 선언이 없으면 온 줄도 모르고 사라진다.
     */
    @get:JsonIgnore
    val unknown: UnknownFields = UnknownFields()

    @JsonAnySetter
    fun capture(name: String, value: Any?) {
        unknown.put(name, value)
    }
}

data class OpenAIGeneratedImage(
    /** base64 로 인코딩된 이미지. URL 이 아니라 바이트가 그대로 온다. */
    @param:JsonProperty("b64_json")
    @get:JsonProperty("b64_json")
    val b64Json: String,
    /** 모델이 프롬프트를 다듬었을 경우의 실제 사용 문구. */
    @param:JsonProperty("revised_prompt")
    @get:JsonProperty("revised_prompt")
    val revisedPrompt: String? = null,
)

/** OpenAI 의 에러 응답 봉투. `{"error": {...}}` */
data class OpenAIErrorEnvelope(val error: OpenAIError? = null)

data class OpenAIError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)
