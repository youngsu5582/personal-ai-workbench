package dev.joyson.aiworkbench.provider.openai

/**
 * OpenAI 이미지 API 의 엔드포인트.
 *
 * 경로를 가르는 축은 모델 세대가 아니라 **작업 종류**다.
 * `/v1` 은 REST API 자체의 버전이라 모델과 무관하고, 세대는 body 의 `model` 필드로 말한다.
 *
 * 값이 하나뿐인 이유는 지금 t2i 만 다루기 때문이다.
 * i2i·인페인팅을 붙이면 `/v1/images/edits` 가 여기 추가된다.
 */
enum class OpenAIImageEndpoint(val path: String) {
    GENERATIONS("/v1/images/generations"),
}

/**
 * 이 어댑터가 다루는 모델 목록.
 *
 * 여기 없는 모델은 `supports` 가 false 를 답한다 — **사실만 답하고 예외는 던지지 않는다.**
 * 대신 처리할지 거절할지는 호출하는 쪽 정책이다.
 *
 * 모델이 세대 정보를 들고 있지 않은 이유: 세대가 바꾸는 것은 경로가 아니라 요청 파라미터인데,
 * 어느 파라미터가 갈리는지 아직 확인된 것이 없다. 실제로 갈리는 것이 나오면 그때 필드를 붙인다.
 */
enum class OpenAIImageModel(val modelName: String) {
    /** 미검증. */
    GPT_IMAGE_1("gpt-image-1"),

    /** 실제 호출로 확인함(2026-09-15). 임의 픽셀 크기를 받는다. */
    GPT_IMAGE_2("gpt-image-2"),

    /** 미검증. */
    GPT_IMAGE_2_SUNBURST("gpt-image-2.5-sunburst"),

    /** 미검증. */
    GPT_IMAGE_2_FLARE("gpt-image-2.5-flare"),
    ;

    companion object {
        fun find(modelName: String): OpenAIImageModel? =
            entries.firstOrNull { it.modelName == modelName }
    }
}
