package dev.joyson.aiworkbench.provider

/** 외부 API 에 보낼 글 생성 요청. */
data class ExternalApiTextRequest(
    val prompt: String,

    /** 모델에게 주는 역할·규칙(system 프롬프트). 없으면 Provider 기본이다. */
    val instructions: String? = null,

    /**
     * 답이 따라야 할 JSON Schema 의 **원문**. null 이면 형식 없는 글을 받는다.
     *
     * 객체가 아니라 문자열인 이유는 **키 순서가 출력 순서**라서다. 객체로 옮기는 단계마다
     * 순서를 지킨다는 보장이 필요한데, 문자열이면 받은 그대로 나간다.
     */
    val jsonSchema: String? = null,

    /**
     * 답하기 전에 얼마나 생각할지. null 이면 보내지 않아 모델 기본값을 따른다.
     *
     * 생각한 만큼이 출력 토큰으로 과금되므로 비용과 지연을 가장 크게 바꾸는 값이다.
     */
    val effort: ReasoningEffort? = null,
) {
    init {
        require(prompt.isNotBlank()) { "prompt 는 비어 있을 수 없다" }
    }
}

/**
 * 포트가 표현하는 추론 노력 눈금. 제품 눈금과 따로 두는 이유는 `ImageQuality` 와 같다.
 *
 * 어느 값을 받는지는 **모델마다 다르다** — 어떤 모델은 [NONE] 을 400 으로 거절한다.
 * 그 사실은 어댑터도 모델 목록으로 들고 있지 않는다. 보내고, 거절되면 그 사유가 실패로 남는다.
 */
enum class ReasoningEffort {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX,
}
