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
) {
    init {
        require(prompt.isNotBlank()) { "prompt 는 비어 있을 수 없다" }
    }
}
