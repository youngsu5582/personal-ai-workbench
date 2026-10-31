package dev.joyson.aiworkbench.provider

data class ExternalApiDecisionResponse(
    /** 질문마다 하나. 비어 있는 응답은 어댑터가 실패로 바꾸므로 여기까지 오지 않는다. */
    val answers: List<Answer>,

    /** Provider 가 이 호출에 대해 말한 사용량·비용. 아무것도 말하지 않는 Provider 는 null 이다. */
    val usage: ProviderUsage? = null,

    /** Provider 가 이 응답에 붙인 식별자. 저쪽 청구서·지원 문의와 대조할 때 쓴다. 없으면 null. */
    val providerId: String? = null,
) {
    sealed interface Answer {
        /** 답한 질문의 이름. 저쪽이 이름을 돌려주지 않으면 null 이다. */
        val name: String?

        /** [probability] 는 0 과 1 사이다. */
        data class Predicate(override val name: String?, val probability: Double) : Answer

        data class Choice(override val name: String?, val choice: String, val confidence: Double?) : Answer

        data class Score(override val name: String?, val score: Double, val confidence: Double?) : Answer

        /** 모델이 이 질문에 답하기를 거절했다. 실패가 아니다 — 저쪽은 처리했고 과금했다. */
        data class Refusal(override val name: String?) : Answer
    }
}
