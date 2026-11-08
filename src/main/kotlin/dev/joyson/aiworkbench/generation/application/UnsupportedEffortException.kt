package dev.joyson.aiworkbench.generation.application

/**
 * 모델을 다루는 Provider 의 API 가 표현하지 못하는 effort 다.
 *
 * 상태 코드를 여기서 정하지 않는 이유는 [UnsupportedModelException] 과 같다.
 * 받을 수 있는 값을 함께 담는 이유도 같다 — 무엇을 보내면 되는지 다른 곳에서 찾게 하지 않는다.
 */
class UnsupportedEffortException(
    val model: String,
    val effort: String,
    val available: List<String>,
) : RuntimeException("이 모델의 Provider 는 effort=$effort 를 표현하지 못한다: $model (받는 값: $available)")
