package dev.joyson.aiworkbench.provider

/**
 * 외부 API 호출이 **어떻게** 실패했는가.
 *
 * HTTP 상태 코드가 아니라 의미로 적는다. 상태 코드를 그대로 올리면 프로토콜이 경계를 넘고,
 * HTTP 가 아닌 Provider(SDK·gRPC)가 오면 채울 수 없다. 번역은 어댑터가 한다.
 *
 * 이 구분이 필요한 이유는 **과금 판정** 때문이다. [ExternalApiException.retryable] 만으로는
 * 셋이 뭉쳐서 못 가린다 — 429·5xx 는 과금되지 않지만 타임아웃은 저쪽이 이미 만들어 과금했을 수 있다.
 * 다시 시도할지와 돈이 나갔는지는 다른 질문이라 각각 답해야 한다.
 */
enum class FailureKind(
    /**
     * 같은 요청을 다시 보내면 결과가 달라질 수 있는 종류인가. **사실이지 정책이 아니다** —
     * 몇 번 재시도할지·언제 포기할지는 `GenerationJobTask.attemptCount` 를 보는 쪽이 정한다.
     */
    val retryable: Boolean,
) {
    /** 요청이 틀렸다(4xx). 다시 보내도 같고, 저쪽은 만들지 않았으니 과금도 없다. */
    REJECTED(retryable = false),

    /** 한도에 걸렸다(429). 기다렸다 다시 하면 되고, 아무것도 만들지 않았으니 과금도 없다. */
    THROTTLED(retryable = true),

    /**
     * 저쪽의 오류다(5xx). 다시 해볼 만하다.
     *
     * 대개 과금되지 않지만 **단정할 수는 없다** — 200 인데 결과물이 없는 응답도 여기 들어오고,
     * 그쪽은 저쪽이 요청을 처리한 뒤라 과금됐을 수 있다.
     */
    PROVIDER_ERROR(retryable = true),

    /**
     * 응답을 받지 못했다 — 타임아웃·연결 끊김.
     *
     * **과금을 가장 의심해야 하는 종류다.** 타임아웃이면 저쪽은 이미 만들어 돈을 받았는데
     * 우리만 못 받았을 수 있다. 그래서 비용을 "0" 으로도 "모름" 으로도 단정하면 안 된다.
     */
    NO_RESPONSE(retryable = true),

    /** 설명되지 않는 실패. 원인을 모르니 다시 시도하지 않는다 — 모르는 채로 세 번 부르면 돈만 세 번 나간다. */
    UNKNOWN(retryable = false),
}
