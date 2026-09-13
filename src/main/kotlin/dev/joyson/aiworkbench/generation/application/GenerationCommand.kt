package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.option.GenerationOption

/**
 * 생성 요청의 입력. api 계층이 HTTP 요청을 이것으로 바꿔 넘긴다.
 */
class GenerationCommand(
    val option: GenerationOption,
    val taskCount: Int,
)
