package dev.joyson.aiworkbench.generation.domain.option

import com.fasterxml.jackson.annotation.JsonInclude
import dev.joyson.aiworkbench.generation.domain.GenerationJobType

/**
 * 글을 만든다. [output] 을 주면 그 JSON Schema 를 따르는 JSON 객체를 받는다.
 *
 * [ImageOption] 처럼 결과물로 가른 종류다. 입력으로 가르면 `text-to-text` · `image-to-text` 처럼
 * 곱으로 늘어나는데, 무엇을 만드는지는 글 하나로 같다. 입력이 늘면 이 타입에 필드로 더한다 —
 * 기본값이 있는 필드는 옛 행을 그대로 읽는다.
 *
 * 형식 없는 글과 구조화된 답도 종류로 나누지 않는다. 바뀌는 것은 받는 모양 하나고,
 * 부를 API 도 결과를 두는 곳도 같다.
 *
 * Provider 별 모델명·파라미터는 여기 담지 않는다 — [ImageOption] 과 같은 이유다.
 *
 * 주지 않은 선택 값은 행에도 적지 않는다(`NON_NULL`). 요청에서 필드를 뺀 것과 같은 모양으로 남아,
 * `"effort": null` 이 "보내지 않았다" 인지 "null 을 보냈다" 인지 따질 일이 없다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TextGenerateOption(
    val prompt: String,

    /** 모델에게 주는 역할·규칙(system 프롬프트). 없으면 Provider 기본이다. */
    val instructions: String? = null,

    val output: TextFormat = TextFormat.Text,

    /** 없으면 보내지 않아 모델 기본값을 따른다. 기본값은 모델마다 다르다. */
    val effort: Effort? = null,
) : GenerationOption {
    init {
        require(prompt.isNotBlank()) { "prompt 는 비어 있을 수 없다" }
        // 빈 문자열과 "주지 않음" 을 같은 뜻으로 받아 주지 않는다. 받는 쪽마다 둘을 가르는 규칙이 생긴다.
        require(instructions == null || instructions.isNotBlank()) { "instructions 를 비우려면 필드를 빼야 한다" }
    }

    override val type: GenerationJobType get() = GenerationJobType.TEXT_GENERATE
}
