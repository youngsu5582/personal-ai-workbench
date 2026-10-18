package dev.joyson.aiworkbench.provider

/** 텍스트와 이미지 근거를 고정된 질문으로 판정해 달라는 요청이다. */
data class ExternalApiDecisionRequest(
    val input: List<DecisionInput>,
    val questions: List<DecisionQuestion>,
) : ExternalApiRequest {
    init {
        require(input.isNotEmpty()) { "판정할 입력이 없다" }
        require(questions.isNotEmpty()) { "판정 질문이 없다" }
        require(questions.map { it.name }.distinct().size == questions.size) { "질문 이름은 서로 달라야 한다" }
    }
}

sealed interface DecisionInput {
    data class Text(val text: String) : DecisionInput {
        init { require(text.isNotBlank()) { "입력 텍스트는 비어 있을 수 없다" } }
    }

    data class Image(val image: ExternalApiFileInput) : DecisionInput
}

sealed interface DecisionQuestion {
    val name: String
    val instructions: String

    data class Predicate(override val name: String, override val instructions: String) : DecisionQuestion
    data class Choice(
        override val name: String,
        override val instructions: String,
        val choices: List<DecisionChoice>,
    ) : DecisionQuestion {
        init {
            require(choices.size >= 2) { "선택지는 둘 이상이어야 한다" }
            require(choices.map { it.value }.distinct().size == choices.size) { "선택지 값은 서로 달라야 한다" }
        }
    }

    data class Score(
        override val name: String,
        override val instructions: String,
        val levels: List<DecisionLevel>,
    ) : DecisionQuestion {
        init { require(levels.size >= 2) { "점수 단계는 둘 이상이어야 한다" } }
    }
}

data class DecisionChoice(val value: String, val description: String)
data class DecisionLevel(val label: String, val description: String)
