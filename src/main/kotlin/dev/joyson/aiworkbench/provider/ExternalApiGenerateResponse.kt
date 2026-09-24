package dev.joyson.aiworkbench.provider

data class ExternalApiGenerateResponse(
    val result: List<ExternalApiGenerateResult>,

    /**
     * Provider 가 이 호출에 대해 말한 사용량·비용. 아무것도 말하지 않는 Provider 는 null 이다.
     *
     * 결과물과 함께 오지만 결과물의 속성은 아니다 — 이미지 두 장을 받아도 호출은 한 번이고
     * 과금도 한 번이다. 그래서 [result] 안이 아니라 밖에 있다.
     */
    val usage: ProviderUsage? = null,
)
