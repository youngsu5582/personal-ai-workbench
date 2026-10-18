package dev.joyson.aiworkbench.generation.domain.option

import dev.joyson.aiworkbench.generation.domain.GenerationJobType

/**
 * 이미지를 만든다. 참조 이미지가 있으면 그것을 바탕으로, 없으면 글만으로.
 *
 * 참조가 있는지로 종류를 나누지 않는다. 있고 없고가 바꾸는 것은 입력 파일을 준비하느냐 하나고,
 * 고칠지 새로 만들지는 prompt 가 말한다. 그 차이로 Provider 의 경로가 갈리는 것은 어댑터의 사정이다.
 *
 * Provider 별 모델명·파라미터는 여기 담지 않는다. adapter 가 변환한다 —
 * 도메인이 특정 Provider 의 표현을 알기 시작하면 Provider 를 바꿀 때 도메인이 따라 바뀐다.
 */
data class ImageOption(
    val prompt: String,
    val size: ImageSize,
    val quality: Quality = Quality.AUTO,

    /**
     * 참조 이미지. 비어 있으면 글만으로 만든다.
     *
     * 지금은 1장까지 받는다. 그런데도 목록인 이유는 **저장된 JSON 의 모양을 미리 고정하기 위해서**다.
     * 나중에 장수를 늘릴 때 상한만 바꾸면 되고, 이미 저장된 행은 그대로 읽힌다.
     */
    val sources: List<FileSource> = emptyList(),
) : GenerationOption {
    init {
        require(prompt.isNotBlank()) { "prompt 는 비어 있을 수 없다" }
        require(sources.size <= MAX_SOURCES) { "참조 이미지는 ${MAX_SOURCES}장까지다" }
    }

    override val type: GenerationJobType get() = GenerationJobType.IMAGE_GENERATE

    companion object {
        /** 늘리려면 Provider 가 몇 장까지 받는지부터 확인해야 한다. */
        const val MAX_SOURCES = 1
    }
}
