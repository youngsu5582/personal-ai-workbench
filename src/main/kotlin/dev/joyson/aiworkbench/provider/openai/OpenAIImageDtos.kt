package dev.joyson.aiworkbench.provider.openai

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import dev.joyson.aiworkbench.provider.UnknownFields
import java.net.URI

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
 * 바이트는 multipart로, 주소를 포함한 요청은 JSON DTO로 옮긴다.
 * 전송 형식의 선택과 필드 대응은 [OpenAIImageClient]가 맡는다.
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
 * 포트의 `ExternalApiFileInput` 과 모양이 같지만 재사용하지 않는다 — 이 파일은 우리 어휘를
 * 모르는 자리고, 옮기는 일은 어댑터가 한다. [OpenAIImageRequest] 와 같은 관계다.
 */
sealed interface OpenAIImageEditImage {
    class Bytes(val bytes: ByteArray, val contentType: String, val filename: String) : OpenAIImageEditImage
    class Url(val url: URI) : OpenAIImageEditImage
}

/** 주소 입력은 JSON의 images[].image_url로 전송한다. multipart의 image[]와 계약이 다르다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpenAIImageEditJsonRequest(
    val model: String,
    val prompt: String,
    val images: List<OpenAIImageReference>,
    val n: Int?,
    val size: String?,
    val quality: String?,
    @param:JsonProperty("output_format")
    @get:JsonProperty("output_format")
    val outputFormat: String?,
)

/** URL은 DTO의 toString에서도 노출하지 않는다. */
class OpenAIImageReference(
    @param:JsonProperty("image_url")
    @get:JsonProperty("image_url")
    val imageUrl: String,
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

    /**
     * 이 호출에 **실제로 쓰인** 값들. 우리가 보낸 것과 다를 수 있다.
     *
     * `background` 는 보내지 않아도 돌아온다 — 저쪽이 기본값을 적용한 결과다.
     * 과금은 요청이 아니라 이쪽을 따라야 하므로 선언해서 받는다.
     */
    val size: String? = null,
    val quality: String? = null,
    val background: String? = null,
    @param:JsonProperty("output_format")
    @get:JsonProperty("output_format")
    val outputFormat: String? = null,
) {
    /**
     * 위에 선언하지 않은 최상위 필드가 여기 담긴다. **그 레벨에서 매핑되지 않은 것만** 온다 —
     * 위에 선언된 것은 절대 여기 오지 않고, 이미지 바이트는 `data` 안이라 구조적으로 섞일 수 없다.
     *
     * 버리더라도 **버렸다는 사실은 알아야** 한다. `size`·`quality`·`background`·`output_format`
     * 이 이렇게 드러났다 — 온 줄도 모르는 것과, 이름이 로그에 떠서 승격하는 것의 차이다.
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

    /** OpenAI 가 이 결과물에 붙인 식별자. 청구서·지원 문의와 대조할 때 쓴다. */
    @param:JsonProperty("generation_id")
    @get:JsonProperty("generation_id")
    val generationId: String? = null,
) {
    /**
     * 결과물 하나 안에서 선언하지 않은 필드.
     *
     * 최상위에만 달면 **여기는 안 덮인다** — 표가 타입마다 따로라 원소 안의 미지 필드는
     * 이쪽 갈고리가 받아야 한다. `b64_json` 은 선언돼 있으니 여기 오지 않는다.
     *
     * 다만 여기는 **이미지가 사는 동네**라, 저쪽이 썸네일이나 마스크를 새 필드로 넣으면
     * 그 바이트가 이 Map 에 들어앉는다. 로그는 [UnknownFields.toString] 이 잘라서 지키지만
     * 메모리는 지키지 못한다. `n > 1` 을 켜는 날 장수만큼 곱해지므로 그때 다시 볼 자리다.
     */
    @get:JsonIgnore
    val unknown: UnknownFields = UnknownFields()

    @JsonAnySetter
    fun capture(name: String, value: Any?) {
        unknown.put(name, value)
    }
}

/** OpenAI 의 에러 응답 봉투. `{"error": {...}}` */
data class OpenAIErrorEnvelope(val error: OpenAIError? = null)

data class OpenAIError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)
