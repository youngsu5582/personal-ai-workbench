package dev.joyson.aiworkbench.provider.domain

import org.springframework.modulith.NamedInterface

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * 만들 이미지의 크기를 말하는 방법.
 *
 * 두 가지가 **하나의 필드**인 이유는, 둘이 보완재가 아니라 대체재이기 때문이다.
 * 비율·해상도와 픽셀을 따로 받으면 "둘 다 왔을 때 뭐가 이기나" · "둘 다 없으면" ·
 * "1:1 인데 1920x1080 이면" 세 가지 규칙을 추가로 만들어야 한다.
 * 필드를 하나로 두면 그 세 상태가 **표현 자체로 불가능**해진다.
 *
 * [ByRatio] 에서 픽셀을 얻는 계산은 결정적이라 무손실이지만, 반대는 손실이다
 * (`1920x1080` 을 `16:9 @ 2k` 로 접으면 `2048x1152` 로 돌아온다).
 * 그래서 사용자가 픽셀을 말할 수 있게 하려면 포트도 픽셀을 그대로 실어야 한다 —
 * 중간에서 비율로 접으면 요청한 크기가 복구되지 않는다.
 *
 * 저장될 때를 대비해 판별자를 둔다. `type` 값은 DB 에 그대로 들어가므로 바꾸면 기존 행을 못 읽는다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ImageSize.ByRatio::class, name = "ratio"),
    JsonSubTypes.Type(value = ImageSize.ByPixels::class, name = "pixels"),
)
@NamedInterface("domain")
sealed interface ImageSize {

    /**
     * 의도로 말한다 — "와이드로, 크게".
     *
     * 픽셀을 특정하지 않으므로 각 어댑터가 자기가 낼 수 있는 가장 가까운 크기로 해석할 여지가 있다.
     */
    @NamedInterface("domain")
    data class ByRatio(
        val ratio: AspectRatio,
        val resolution: Resolution,
    ) : ImageSize

    /**
     * 결과로 말한다 — "정확히 1920x1080".
     *
     * Provider 가 64의 배수 같은 격자만 받으면 어댑터가 깎게 되는데,
     * 비율과 달리 사용자가 **정확한 값을 요청했으므로** 깎였다는 사실을 응답으로 알려야 한다.
     */
    @NamedInterface("domain")
    data class ByPixels(
        val width: Int,
        val height: Int,
    ) : ImageSize {
        init {
            require(width > 0 && height > 0) { "가로·세로는 양수여야 한다" }
        }
    }
}
