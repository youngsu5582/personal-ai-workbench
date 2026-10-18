package dev.joyson.aiworkbench.provider

/** 외부 AI Provider 호출 한 건의 응답 공통 표지다. */
sealed interface ExternalApiResponse {
    /** 사용량은 결과 종류가 아니라 호출 자체의 속성이다. */
    val usage: ProviderUsage?
}
