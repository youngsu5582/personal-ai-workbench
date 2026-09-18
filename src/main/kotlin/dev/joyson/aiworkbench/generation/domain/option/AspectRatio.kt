package dev.joyson.aiworkbench.generation.domain.option


import com.fasterxml.jackson.annotation.JsonValue

/**
 * 만들 이미지의 가로세로 비율.
 *
 * 목록은 **Provider 메뉴의 합집합이 아니라 우리가 제공하기로 한 선택지**다.
 * 어떤 Provider 는 여기 없는 비율을 지원하고, 어떤 Provider 는 여기 있는 비율을 못 한다.
 * 못 하는 경우를 어떻게 맞출지(가장 가까운 비율로 깎을지, 거절할지)는 각 어댑터의 번역표에 적힌다 —
 * 그 표가 곧 "우리 제품에서 16:9 는 무엇을 뜻하는가" 라는 결정의 기록이다.
 *
 * [width]·[height] 를 함께 갖는 이유는 어댑터가 `"16:9"` 를 파싱하지 않게 하기 위해서다.
 * 해상도와 곱해 실제 픽셀 크기를 내거나, Provider 가 지원하는 크기 중 가장 가까운 것을 고를 때 쓴다.
 */
enum class AspectRatio(
    @get:JsonValue val value: String,
    /** 비율의 가로 쪽 수. 픽셀이 아니다. */
    val width: Int,
    /** 비율의 세로 쪽 수. 픽셀이 아니다. */
    val height: Int,
) {
    /** 정사각. 프로필·썸네일. 거의 모든 Provider 가 지원한다. */
    ONE_ONE("1:1", 1, 1),

    /** 가로 사진. 모니터·프레젠테이션. */
    FOUR_THREE("4:3", 4, 3),

    /** 세로 사진. */
    THREE_FOUR("3:4", 3, 4),

    /** 가로 인화 사진(35mm). */
    THREE_TWO("3:2", 3, 2),

    /** 세로 인화 사진. */
    TWO_THREE("2:3", 2, 3),

    /** 와이드. 영상 썸네일·배너. */
    SIXTEEN_NINE("16:9", 16, 9),

    /** 세로 와이드. 모바일 전체화면·숏폼. */
    NINE_SIXTEEN("9:16", 9, 16),
    ;

    /** 가로÷세로. Provider 가 지원하는 크기 중 가장 가까운 것을 고를 때 쓴다. */
    val ratio: Double get() = width.toDouble() / height

    /** 가로가 세로보다 긴가. 세로형만 지원하는 Provider 를 거를 때 쓴다. */
    val isLandscape: Boolean get() = width > height
}
