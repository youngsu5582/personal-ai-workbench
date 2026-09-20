package dev.joyson.aiworkbench.provider.openai

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

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

/**
 * `/v1/images/edits` 요청.
 *
 * **이 파일에서 유일하게 Jackson 애노테이션이 없는 타입이다.** multipart 로 나가므로 직렬화를
 * 거치지 않는다 — 필드 이름과 폼 키의 대응은 [OpenAIImageClient] 의 파트 빌더가 정본이다.
 *
 * `data class` 가 아닌 이유는 [images] 의 원소가 `ByteArray` 를 품어서다.
 *
 * 응답은 generations 와 같은 모양이라 [OpenAIImageResponse] 를 그대로 쓴다.
 */
class OpenAIImageEditRequest(
    val model: String,
    val prompt: String,
    val images: List<OpenAIImageEditImage>,
    /** 1–10. 호출 1건이 이미지 1장이므로 지금은 항상 1이다. */
    val n: Int? = null,
    /** 커스텀 `WIDTHxHEIGHT`. 양변이 16의 배수여야 한다. */
    val size: String? = null,
    /** `low` · `medium` · `high` · `xhigh` · `max` · `auto` */
    val quality: String? = null,
    val outputFormat: String? = null,
) {
    init {
        require(images.isNotEmpty()) { "고칠 이미지가 없다" }
    }
}

/**
 * 파트 하나로 실릴 이미지.
 *
 * 포트의 `ExternalApiImageInput` 과 모양이 같지만 재사용하지 않는다 — 이 파일은 우리 어휘를
 * 모르는 자리고, 옮기는 일은 어댑터가 한다. [OpenAIImageRequest] 와 같은 관계다.
 */
class OpenAIImageEditImage(
    val bytes: ByteArray,
    /** 파트의 `Content-Type`. 없으면 받는 쪽이 확장자로 형식을 추측하게 된다. */
    val contentType: String,
    /** 파트의 `filename`. 이 값이 있어야 파일로 취급된다. */
    val filename: String,
)

data class OpenAIImageResponse(
    val created: Long = 0,
    val data: List<OpenAIGeneratedImage> = emptyList(),
)

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
