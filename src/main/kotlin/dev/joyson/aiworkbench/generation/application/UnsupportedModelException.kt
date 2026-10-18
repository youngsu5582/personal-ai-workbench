package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.GenerationJobType

/**
 * 이 모델로 이 종류의 작업을 다룰 수 있는 Provider 가 없다.
 *
 * 상태 코드를 여기서 정하지 않는다. 이 계층은 HTTP 를 모르고, 예외를 응답으로 옮기는 일은
 * api 계층의 몫이다([dev.joyson.aiworkbench.generation.api.GenerationApiExceptionHandler]).
 *
 * 가능한 Provider 목록을 함께 담는 이유: 클라이언트가 무엇을 쓸 수 있는지 다른 곳에서
 * 찾아야 하면 이 오류가 쓸모없어진다.
 */
class UnsupportedModelException(
    val model: String,
    /** 모델이 있어도 종류가 안 맞으면 걸린다. 종류를 함께 말해야 어느 쪽을 고칠지 안다. */
    val type: GenerationJobType,
    val available: List<String>,
) : RuntimeException("${type.value} 작업을 다룰 수 없는 모델이다: $model (가능한 Provider: $available)")
