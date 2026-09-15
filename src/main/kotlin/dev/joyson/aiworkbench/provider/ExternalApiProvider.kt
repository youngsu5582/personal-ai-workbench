package dev.joyson.aiworkbench.provider

/**
 * 외부 이미지 생성 API 한 곳을 감싸는 어댑터.
 *
 * 이 인터페이스는 **표준 어휘만 안다.** 모델명이 어떤 파라미터로 들어가는지, 인증을 어떻게 하는지,
 * 응답이 base64 인지 URL 인지는 전부 구현체 안에 갇힌다.
 * 그래서 Provider 를 추가해도 호출하는 쪽은 이 파일만 보면 된다.
 *
 * 구현체는 **사실만 답한다.** 못 하는 요청을 만나도 대신 판단하지 않는다 —
 * 다른 Provider 로 넘길지, 4xx 를 줄지, 비슷한 값으로 깎을지는 정책이고 호출하는 쪽이 정한다.
 */
interface ExternalApiProvider {

    /**
     * 어느 외부 API 였는지 로그·메트릭에서 구분하기 위한 이름. `openai` 처럼 짧게.
     *
     * 이름으로 Provider 를 고르라고 있는 게 아니다. 고르는 기준은 [supports] 다 —
     * 이름으로 고르면 호출하는 쪽이 "openai" 라는 문자열을 알아야 하고, 그건 결합이다.
     */
    val name: String

    /**
     * 이 모델을 다룰 수 있는가.
     *
     * 사실 질의라 예외를 던지지 않는다. 모르는 모델이면 그냥 false 다.
     * 모델을 인자로 받는 이유는 Provider 하나가 여러 모델을 다루기 때문이다
     * (`gpt-image-2.5-sunburst` · `gpt-image-2.5-flare`).
     *
     * 모델이 [ExternalApiGenerateRequest] 안이 아니라 밖에 있는 이유:
     * 요청은 "무엇을 만들까" 이고 모델은 "무엇으로 만들까" 다. 같은 요청을 다른 모델로
     * 다시 돌리는 일이 생기는데, 모델이 요청 안에 있으면 요청을 복제해야 한다.
     */
    fun supports(model: String): Boolean

    /**
     * 외부 API 호출 **1건**. `GenerationJobTask` 1행에 대응한다.
     *
     * 결과가 목록인 이유는 호출 한 번이 이미지 여러 장을 줄 수 있어서다.
     * 몇 장을 만들지는 아직 [ExternalApiGenerateRequest] 에 없다 — 지금은 Provider 기본값(1장)이다.
     *
     * 실패는 [ExternalApiException] 으로 알린다. 구현체는 자기 API 의 상태 코드를
     * 재시도 가능 여부로 **번역**할 책임만 진다. 실제로 재시도할지는 호출하는 쪽이 정한다.
     *
     * 이 시그니처는 **동기**다. 부르면 결과가 온다.
     * 제출하고 나중에 수확하는 Provider(영상 생성 쪽이 그럴 가능성이 높다)가 들어오면
     * `submit` / `poll` 로 갈라야 한다. 그때 이 메서드는 바뀐다.
     */
    fun generate(model: String, request: ExternalApiGenerateRequest): ExternalApiGenerateResponse
}
