package dev.joyson.aiworkbench.generation.domain.option

import dev.joyson.aiworkbench.generation.domain.GenerationJobType

/**
 * 텍스트로 이미지를 만든다.
 *
 * Provider 별 모델명·파라미터는 여기 담지 않는다. adapter 가 변환한다 —
 * 도메인이 특정 Provider 의 표현을 알기 시작하면 Provider 를 바꿀 때 도메인이 따라 바뀐다.
 */
data class TextToImageOption(
    val prompt: String,
    val size: ImageSize,
    val quality: Quality = Quality.AUTO,
) : GenerationOption {
    init {
        require(prompt.isNotBlank()) { "prompt 는 비어 있을 수 없다" }
    }

    override val type: GenerationJobType get() = GenerationJobType.TEXT_TO_IMAGE
}
