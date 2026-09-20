package dev.joyson.aiworkbench.provider.openai

/**
 * OpenAI 이미지 API 의 엔드포인트.
 *
 * 경로를 가르는 축은 모델 세대가 아니라 **작업 종류**다.
 * `/v1` 은 REST API 자체의 버전이라 모델과 무관하고, 세대는 body 의 `model` 필드로 말한다.
 *
 * 두 경로는 전송 형식도 다르다 — [GENERATIONS] 는 JSON 이고 [EDITS] 는 multipart 다.
 * 파일을 싣는 형식이 JSON 밖에 있기 때문이라, 경로만 바꿔서는 보낼 수 없다.
 */
enum class OpenAIImageEndpoint(val path: String) {
    GENERATIONS("/v1/images/generations"),

    /** 입력 이미지를 고친다. 인페인팅(`mask`)도 이 경로지만 아직 쓰지 않는다. */
    EDITS("/v1/images/edits"),
}

/**
 * 이 어댑터가 다루는 모델 목록.
 *
 * 여기 없는 모델은 `supports` 가 false 를 답한다 — **사실만 답하고 예외는 던지지 않는다.**
 * 대신 처리할지 거절할지는 호출하는 쪽 정책이다.
 *
 * 모델이 세대 정보를 들고 있지 않은 이유: 세대가 바꾸는 것은 경로가 아니라 요청 파라미터인데,
 * 어느 파라미터가 갈리는지 아직 확인된 것이 없다. 실제로 갈리는 것이 나오면 그때 필드를 붙인다.
 *
 * **모델은 작업 종류도 가르지 않는다** — 같은 모델이 generations 와 edits 양쪽에 쓰인다.
 * 그래서 `supports` 는 모델만 묻는다. 한 종류만 할 수 있는 모델이 나오면 그때 인자를 늘린다.
 */
enum class OpenAIImageModel(val modelName: String) {
    /** 미검증. */
    GPT_IMAGE_1("gpt-image-1"),

    /**
     * generations 는 실제 호출로 확인함(2026-09-15). 임의 픽셀 크기를 받는다.
     * edits 는 문서상 지원하지만 미검증.
     */
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
