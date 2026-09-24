package dev.joyson.aiworkbench.provider

/**
 * 외부 API 호출 실패.
 *
 * 호출하는 쪽이 `OpenAIException` 같은 Provider 별 예외를 직접 잡으면
 * Provider 를 추가할 때마다 catch 절이 늘어난다. 그래서 경계에서 이 한 종류로 번역한다.
 *
 * [retryable] 을 따로 받지 않고 [kind] 에서 파생시킨다. 둘을 각각 받으면 어긋날 수 있고,
 * 어긋나면 "429 인데 다시 못 한다" 같은 말이 안 되는 행이 조용히 생긴다.
 */
class ExternalApiException(
    /** 어떻게 실패했는가. 재시도 판단과 **과금 판정**이 둘 다 여기서 나온다. */
    val kind: FailureKind,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /**
     * 같은 요청을 다시 보내면 결과가 달라질 수 있는 종류인가. **사실이지 정책이 아니다** —
     * 몇 번 재시도할지·언제 포기할지는 `GenerationJobTask.attemptCount` 를 보는 쪽이 정한다.
     */
    val retryable: Boolean get() = kind.retryable
}
