package dev.joyson.aiworkbench.generation.domain.option

import com.fasterxml.jackson.annotation.JsonValue

/**
 * 답하기 전에 얼마나 생각할지. 저장·전송 표기를 [value] 로 고정한다 — [Quality] 와 같은 이유다.
 *
 * 생각한 만큼이 출력 토큰으로 과금된다. 같은 모델 · 같은 프롬프트라도 이 값에 따라 비용과 지연이 크게 갈려서
 * 부르는 쪽이 요청마다 고른다.
 *
 * 어느 값을 받는지는 모델마다 다르다(어떤 모델은 `none` 을 거절한다). 여기서 막지 않는다 —
 * 모델별 허용표를 우리가 따로 들고 있으면 저쪽이 바꿀 때마다 어긋난다. 거절되면 그 사유가 Task 실패로 남는다.
 */
enum class Effort(@get:JsonValue val value: String) {
    NONE("none"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max"),
}
