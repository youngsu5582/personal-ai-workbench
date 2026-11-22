package dev.joyson.aiworkbench.generation.application

/**
 * 모델을 다루는 Provider 가 모르는 목소리다.
 *
 * 상태 코드를 여기서 정하지 않는 이유와 받을 수 있는 값을 함께 담는 이유는 [UnsupportedEffortException] 과 같다.
 */
class UnsupportedVoiceException(
    val model: String,
    val voice: String,
    val available: List<String>,
) : RuntimeException("이 모델의 Provider 는 voice=$voice 를 모른다: $model (받는 값: $available)")
