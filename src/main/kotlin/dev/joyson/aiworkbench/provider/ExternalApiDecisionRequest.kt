package dev.joyson.aiworkbench.provider

/**
 * 근거([input])를 정해 둔 질문([questions])으로 판정해 달라는 요청.
 *
 * 질문 종류마다 답의 모양이 다르다 — [Question.Predicate] 는 확률, [Question.Choice] 는 선택지 하나,
 * [Question.Score] 는 단계 하나다. 답은 [ExternalApiDecisionResponse.Answer] 가 같은 갈래로 받는다.
 */
data class ExternalApiDecisionRequest(
    /** 판정할 근거. 순서가 곧 모델이 읽는 순서다. */
    val input: List<Input>,
    val questions: List<Question>,
) {
    init {
        require(input.isNotEmpty()) { "판정할 근거가 없다" }
        require(questions.isNotEmpty()) { "판정할 질문이 없다" }
        // 답은 이름으로 질문을 가리킨다. 겹치면 어느 질문의 답인지 알 수 없다.
        require(questions.map { it.name }.distinct().size == questions.size) { "질문 이름은 서로 달라야 한다" }
    }

    sealed interface Input {
        data class Text(val text: String) : Input {
            init { require(text.isNotBlank()) { "근거 글은 비어 있을 수 없다" } }
        }

        data class Image(val image: ExternalApiFileInput) : Input
    }

    sealed interface Question {
        /** 답이 이 이름으로 돌아온다. */
        val name: String
        val instructions: String

        /** 참일 확률을 묻는다. */
        data class Predicate(override val name: String, override val instructions: String) : Question

        /** [choices] 중 하나를 고르게 한다. */
        data class Choice(
            override val name: String,
            override val instructions: String,
            val choices: List<Option>,
        ) : Question

        /** [levels] 중 하나에 놓게 한다. 목록의 순서가 단계의 순서다. */
        data class Score(
            override val name: String,
            override val instructions: String,
            val levels: List<Option>,
        ) : Question
    }

    /** 선택지 하나 또는 단계 하나. [value] 가 답에 그대로 돌아온다. */
    data class Option(val value: String, val description: String)
}
