package dev.joyson.aiworkbench.generation.domain.option

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dev.joyson.aiworkbench.generation.domain.GenerationJobType

/** 텍스트와 이미지 근거를 정해진 질문으로 판정하는 작업의 입력이다. */
data class DecisionOption(
    val input: List<DecisionEvidence>,
    val questions: List<DecisionQuestion>,
) : GenerationOption {
    override val type = GenerationJobType.DECISION

    init {
        require(input.isNotEmpty()) { "판정할 입력이 없다" }
        require(questions.isNotEmpty()) { "판정 질문이 없다" }
        require(questions.map { it.name }.distinct().size == questions.size) { "질문 이름은 서로 달라야 한다" }
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DecisionEvidence.Text::class, name = "text"),
    JsonSubTypes.Type(value = DecisionEvidence.Image::class, name = "image"),
)
sealed interface DecisionEvidence {
    data class Text(val text: String) : DecisionEvidence {
        init { require(text.isNotBlank()) { "입력 텍스트는 비어 있을 수 없다" } }
    }
    data class Image(val source: FileSource) : DecisionEvidence
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DecisionQuestion.Predicate::class, name = "predicate"),
    JsonSubTypes.Type(value = DecisionQuestion.Choice::class, name = "choice"),
    JsonSubTypes.Type(value = DecisionQuestion.Score::class, name = "score"),
)
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
