package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.option.GenerationOption

/**
 * 생성 요청의 입력. api 계층이 HTTP 요청을 이것으로 바꿔 넘긴다.
 */
class GenerationCommand(
    val option: GenerationOption,
    /** 무엇으로 만들 것인가. 접수 시점에 다룰 수 있는 모델인지 확인된 값이다. */
    val model: String,
    val taskCount: Int,
)
