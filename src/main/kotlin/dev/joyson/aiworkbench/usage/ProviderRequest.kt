package dev.joyson.aiworkbench.usage

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Provider 에게 보낸 요청을 **표준 어휘로** 적은 것.
 *
 * 요청에 적힌 `16:9 @2k` 가 아니라 그것을 푼 `2048x1152` 다 — 그 값은 Job 어디에도 없어서
 * 여기 적어 두지 않으면 되살릴 수 없다. 반대로 어댑터가 더한 전송 세부(`n`·`output_format`)는
 * 여기 없다. **포트에 있는 것만 적는다.**
 *
 * 원문이 아니라 표준형인 이유: 이 값으로 Provider 를 **가로질러** 비교한다.
 * 응답(`usageRaw`)을 원문으로 두는 것과 반대 방향인데, 갈리는 기준은 **모양을 누가 정하느냐**다.
 * 응답은 저쪽이 정하니 잃으면 못 되찾고, 요청은 우리가 정하니 표준형이 낫다.
 *
 * 컬럼이 아니라 JSON 인 이유는 **생성 종류마다 축이 다르기** 때문이다. 이미지는 픽셀과 품질이지만
 * 영상은 길이와 프레임이다. 컬럼으로 두면 종류가 늘 때마다 다른 종류의 행에서 null 인 칸이 생기고,
 * 그 null 은 "모른다" 가 아니라 "해당 없음" 이라 뜻이 섞인다.
 * `GenerationOption` 이 JSON 인 것과 같은 이유이고, 이건 그것을 푼 결과다.
 *
 * 판별자는 JSON 안의 `type` 이다. 그 값이 DB 행에 그대로 박히므로 리네임하면 옛 행을 못 읽는다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ImageRequest::class, name = "image"),
)
sealed interface ProviderRequest

/**
 * 이미지 한 장을 만들라고 보냈다.
 *
 * 토큰을 주지 않고 **크기·품질이 곧 단가**인 Provider 에서는 이 값들이 유일한 계산 근거가 된다.
 */
data class ImageRequest(
    val width: Int,
    val height: Int,
    val quality: String? = null,

    /**
     * 우리가 보내지 않아도 Provider 가 기본값을 적용해 답해주는 값들.
     *
     * 단가 축인지 아직 모른다. 모르는 채로 버리면 나중에 되살릴 수 없고, JSON 이라 자리 값도 없다.
     */
    val background: String? = null,
    val outputFormat: String? = null,
) : ProviderRequest {
    init {
        require(width > 0 && height > 0) { "가로·세로는 양수여야 한다" }
    }
}
