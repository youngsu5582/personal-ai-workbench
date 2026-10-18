package dev.joyson.aiworkbench.provider

/**
 * 외부 텍스트 생성 API 한 곳을 감싸는 어댑터.
 *
 * 이미지 포트([ExternalApiProvider])와 나눠 둔 이유는 **모델이 서로의 일을 못 하기** 때문이다.
 * 한 포트에 두면 `supports(model)` 이 "이 모델을 아는가" 만 답해, 이미지 모델로 글을 요청해도
 * 접수가 통과하고 몇 초 뒤 Task 실패로만 드러난다. 포트가 갈려 있으면 찾는 일 자체가 종류를 묻는다.
 *
 * 규칙은 이미지 포트와 같다 — 표준 어휘만 알고, 구현체는 사실만 답한다.
 * 모델이 거절했거나 중간에 멈춘 응답도 **사실로** 돌려준다([ExternalApiTextResponse.stopReason]).
 * 그것을 실패로 볼지는 부르는 쪽이 정한다.
 */
interface ExternalApiTextProvider {

    /** 어느 외부 API 였는지 로그·메트릭에서 구분하기 위한 이름. 고르는 기준은 [supports] 다. */
    val name: String

    /** 이 모델로 글을 만들 수 있는가. 사실 질의라 모르는 모델이면 예외가 아니라 false 다. */
    fun supports(model: String): Boolean

    /**
     * 외부 API 호출 **1건**. `GenerationJobTask` 1행에 대응한다.
     *
     * 실패는 [ExternalApiException] 으로 알린다. 응답을 받았으면 — 모델이 거절했든 잘렸든 — 실패가 아니다.
     * 그 응답에는 이미 과금된 사용량이 실려 있어서, 예외로 바꾸면 그 돈의 기록이 사라진다.
     *
     * 이미지 포트처럼 **동기**다.
     */
    fun generateText(model: String, request: ExternalApiTextRequest): ExternalApiTextResponse
}
