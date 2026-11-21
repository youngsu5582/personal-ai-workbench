package dev.joyson.aiworkbench.generation.domain.option

import com.fasterxml.jackson.annotation.JsonInclude
import dev.joyson.aiworkbench.generation.domain.GenerationJobType

/**
 * 글을 소리로 읽는다. 결과물은 오디오 파일 하나다.
 *
 * [TextGenerateOption] 의 `prompt` 와 이름을 나누는 이유는 [text] 가 지시가 아니라 **그대로 읽히는 글**이라서다.
 * 말투를 글 속에 쓰면 그 지시까지 소리 내어 읽히므로 [style] 로 따로 받는다.
 *
 * 받을 수 있는 목소리는 모델을 다루는 Provider 가 답한다. 그래서 여기서는 비어 있지 않은지만 보고,
 * 이름 확인은 접수가 한다.
 *
 * 주지 않은 선택 값은 행에도 적지 않는다(`NON_NULL`) — [TextGenerateOption] 과 같은 이유다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AudioGenerateOption(
    val text: String,

    /** 목소리 이름(`Kore` 등). 대소문자를 가린다. */
    val voice: String,

    /** 어떻게 읽을지("차분하고 또렷하게"). 없으면 말투 지시를 보내지 않는다. */
    val style: String? = null,
) : GenerationOption {
    init {
        require(text.isNotBlank()) { "text 는 비어 있을 수 없다" }
        require(voice.isNotBlank()) { "voice 는 비어 있을 수 없다" }
        // 빈 문자열과 "주지 않음" 을 같은 뜻으로 받아 주지 않는다. TextGenerateOption.instructions 와 같다.
        require(style == null || style.isNotBlank()) { "style 을 비우려면 필드를 빼야 한다" }
    }

    override val type: GenerationJobType get() = GenerationJobType.AUDIO_GENERATE
}
