package dev.joyson.aiworkbench.provider

/**
 * 정해 둔 질문에 타입이 정해진 답(확률 · 선택지 · 단계)을 주는 외부 API 한 곳을 감싸는 어댑터.
 *
 * 이미지 포트([ExternalApiProvider]) · 텍스트 포트([ExternalApiTextProvider])와 나눠 둔 이유는 같다 —
 * 판정 모델은 이미지도 자유 글도 만들지 않는다. 포트가 갈려 있어야 찾는 일 자체가 종류를 묻는다.
 *
 * 규칙도 같다 — 표준 어휘만 알고, 구현체는 사실만 답한다.
 * 모델이 어떤 질문에 답하기를 거절한 것도 실패가 아니라 답의 하나다([ExternalApiDecisionResponse.Answer.Refusal]).
 */
interface ExternalApiDecisionProvider {

    /** 어느 외부 API 였는지 로그·메트릭에서 구분하기 위한 이름. 고르는 기준은 [supports] 다. */
    val name: String

    /** 이 모델로 판정할 수 있는가. 사실 질의라 모르는 모델이면 예외가 아니라 false 다. */
    fun supports(model: String): Boolean

    /**
     * 외부 API 호출 **1건**. `GenerationJobTask` 1행에 대응한다. 질문이 여럿이어도 호출은 한 번이다.
     *
     * 실패는 [ExternalApiException] 으로 알린다. 응답을 받은 뒤 그것을 옮기다 난 실패도 마찬가지다 —
     * 그때는 저쪽이 이미 처리해 과금했으므로, 과금이 없다는 뜻의 [FailureKind.REJECTED] 로 알리지 않는다.
     *
     * 다른 포트처럼 **동기**다.
     */
    fun decide(model: String, request: ExternalApiDecisionRequest): ExternalApiDecisionResponse
}
