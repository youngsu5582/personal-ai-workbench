package dev.joyson.aiworkbench.provider

/**
 * 호출의 종류. Provider 를 고를 때 모델과 함께 묻는다.
 *
 * 모델만 물으면 판정 모델로 이미지를 만들라는 요청도 "다룰 수 있다" 를 받는다.
 * 그 요청은 접수된 뒤에야 Task 실패로 드러나고, 그 실패는 다시 해도 같다.
 *
 * [ExternalApiRequest] 의 갈래와 하나씩 대응한다. 요청을 만들기 전(접수 시점)에도 물어야 해서
 * 요청 타입과 따로 둔다.
 */
enum class ExternalApiKind {
    /** [ExternalApiGenerateRequest]. 글에서 만들기와 이미지 고치기가 둘 다 여기다. */
    GENERATE,

    /** [ExternalApiDecisionRequest] */
    DECISION,
}
