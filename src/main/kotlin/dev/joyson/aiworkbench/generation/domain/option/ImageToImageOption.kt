package dev.joyson.aiworkbench.generation.domain.option

import dev.joyson.aiworkbench.generation.domain.GenerationJobType

/**
 * 이미 있는 이미지를 프롬프트로 고친다.
 *
 * [TextToImageOption] 에 입력 이미지를 덧붙이는 대신 타입을 나눈 이유는, 그렇게 하면
 * "이미지가 있으면 고치고 없으면 만든다" 가 되어 **무엇을 요청했는지를 값으로만 알 수 있기**
 * 때문이다. 종류가 타입으로 갈려 있어야 분기와 검증이 빠짐없이 강제된다.
 *
 * Provider 별 모델명·파라미터는 여기 담지 않는다 — [TextToImageOption] 과 같은 이유다.
 */
data class ImageToImageOption(
    val prompt: String,

    /**
     * 고칠 대상.
     *
     * 지금은 1장만 받는다. 그런데도 목록인 이유는 **저장된 JSON 의 모양을 미리 고정하기 위해서**다.
     * 나중에 장수를 늘릴 때 상한만 바꾸면 되고, 이미 저장된 행은 그대로 읽힌다.
     */
    val sources: List<ImageSource>,

    val size: ImageSize,
    val quality: Quality = Quality.AUTO,
) : GenerationOption {
    init {
        require(prompt.isNotBlank()) { "prompt 는 비어 있을 수 없다" }
        require(sources.size == MAX_SOURCES) { "입력 이미지는 ${MAX_SOURCES}장이어야 한다" }
    }

    override val type: GenerationJobType get() = GenerationJobType.IMAGE_TO_IMAGE

    companion object {
        /** 늘리려면 Provider 가 몇 장까지 받는지부터 확인해야 한다. */
        const val MAX_SOURCES = 1
    }
}
