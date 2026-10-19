package dev.joyson.aiworkbench.provider

data class ExternalApiTextResponse(
    /**
     * 모델이 낸 글. 스키마를 줬으면 그것을 따르는 JSON 이다 — [stopReason] 이 [StopReason.COMPLETED] 일 때만.
     *
     * 거절했으면 거절 문구이고, 잘렸으면 잘린 데까지다. 아무것도 내지 않았으면 빈 문자열이다.
     */
    val text: String,

    val stopReason: StopReason,

    /** Provider 가 이 호출에 대해 말한 사용량·비용. 아무것도 말하지 않는 Provider 는 null 이다. */
    val usage: ProviderUsage? = null,

    /**
     * Provider 가 이 응답에 붙인 식별자. 없으면 null.
     *
     * 저쪽 청구서·지원 문의와 대조할 때 쓴다. 우리 uuid 는 저쪽이 모르고, 저쪽 id 는 우리가
     * 안 적으면 다시 알 길이 없다 — 응답은 한 번뿐이다.
     */
    val providerId: String? = null,
)

/**
 * 모델이 **왜** 멈췄는가.
 *
 * 응답은 왔지만 쓸 수 없는 경우를 실패 대신 이 값으로 말한다. 이 경우들은 저쪽이 이미 만든 뒤라
 * 과금됐고, 예외로 바꾸면 사용량이 함께 버려진다. 쓸 수 있는지는 부르는 쪽이 정한다 —
 * 잘린 글도 쓸모 있는 곳이 있다.
 */
enum class StopReason {
    /** 끝까지 답했다. */
    COMPLETED,

    /** 출력 토큰 한도에 걸려 잘렸다. 스키마를 줬다면 JSON 이 닫히지 않았다. */
    MAX_TOKENS,

    /** 콘텐츠 필터가 중간에 멈췄다. */
    CONTENT_FILTER,

    /**
     * 위 둘 말고 다른 이유로 끝까지 가지 못했다(`max_messages` 등). 이유는 어댑터가 로그에 남긴다.
     *
     * 따로 이름을 붙이지 않는 이유는 부르는 쪽이 이것들을 다르게 다룰 일이 없어서다. 이유는 Provider 가 늘려 가므로
     * 하나씩 이름을 붙이면 늘 한 발 늦고, 늦은 동안 그 응답은 예외가 되어 사용량을 잃는다.
     */
    INCOMPLETE,

    /** 모델이 답하기를 거절했다. [ExternalApiTextResponse.text] 가 거절 문구다. */
    REFUSED,
}
