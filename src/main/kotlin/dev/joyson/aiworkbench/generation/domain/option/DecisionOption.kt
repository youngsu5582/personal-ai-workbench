package dev.joyson.aiworkbench.generation.domain.option

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dev.joyson.aiworkbench.generation.domain.GenerationJobType

/**
 * 근거([input])를 정해 둔 질문([questions])으로 판정한다. 결과물은 질문마다 하나인 타입이 정해진 답이다.
 *
 * [TextGenerateOption] 과 결과물이 다르다 — 글이 아니라 확률 · 선택지 · 단계라 받는 쪽이 파싱하지 않는다.
 * 그래서 JSON Schema 를 주는 글 생성으로 대신하지 않고 종류를 나눈다.
 *
 * 한 호출이 질문을 모두 답하므로 Job 은 Task 하나만 갖는다([dev.joyson.aiworkbench.generation.domain.GenerationJob]).
 */
data class DecisionOption(
    /** 판정할 근거. 순서가 곧 모델이 읽는 순서다. */
    val input: List<DecisionEvidence>,
    val questions: List<DecisionQuestion>,
) : GenerationOption {
    init {
        require(input.isNotEmpty()) { "판정할 근거가 없다" }
        require(questions.isNotEmpty()) { "판정할 질문이 없다" }
        // 답은 이름으로 질문을 가리킨다. 겹치면 어느 질문의 답인지 알 수 없다.
        require(questions.map { it.name }.distinct().size == questions.size) { "질문 이름은 서로 달라야 한다" }
    }

    override val type: GenerationJobType get() = GenerationJobType.DECISION

    /** 근거 중 이미지가 가리키는 파일. 접수 때 소유를 확인하고 실행 때 읽어 넘긴다. */
    @get:JsonIgnore
    val sources: List<FileSource> get() = input.filterIsInstance<DecisionEvidence.Image>().map { it.source }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DecisionEvidence.Text::class, name = "text"),
    JsonSubTypes.Type(value = DecisionEvidence.Image::class, name = "image"),
)
sealed interface DecisionEvidence {
    data class Text(val text: String) : DecisionEvidence {
        init { require(text.isNotBlank()) { "근거 글은 비어 있을 수 없다" } }
    }

    data class Image(val source: FileSource) : DecisionEvidence
}

/** 질문의 종류가 답의 모양을 정한다. 판별자가 행에 그대로 들어가므로 바꾸면 기존 행을 못 읽는다. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DecisionQuestion.Predicate::class, name = "predicate"),
    JsonSubTypes.Type(value = DecisionQuestion.Choice::class, name = "choice"),
    JsonSubTypes.Type(value = DecisionQuestion.Score::class, name = "score"),
)
sealed interface DecisionQuestion {
    /** 답이 이 이름으로 돌아온다. */
    val name: String
    val instructions: String

    /** 참일 확률을 묻는다. */
    data class Predicate(override val name: String, override val instructions: String) : DecisionQuestion {
        init { requireText(name, instructions) }
    }

    /** [choices] 중 하나를 고르게 한다. */
    data class Choice(
        override val name: String,
        override val instructions: String,
        val choices: List<DecisionChoice>,
    ) : DecisionQuestion {
        init {
            requireText(name, instructions)
            require(choices.size >= 2) { "선택지는 둘 이상이어야 한다" }
            require(choices.map { it.value }.distinct().size == choices.size) { "선택지 값은 서로 달라야 한다" }
        }
    }

    /** [levels] 중 하나에 놓게 한다. 목록의 순서가 단계의 순서다. */
    data class Score(
        override val name: String,
        override val instructions: String,
        val levels: List<DecisionLevel>,
    ) : DecisionQuestion {
        init {
            requireText(name, instructions)
            require(levels.size >= 2) { "단계는 둘 이상이어야 한다" }
            require(levels.map { it.label }.distinct().size == levels.size) { "단계 이름은 서로 달라야 한다" }
        }
    }
}

private fun requireText(name: String, instructions: String) {
    require(name.isNotBlank()) { "질문 이름은 비어 있을 수 없다" }
    require(instructions.isNotBlank()) { "질문은 비어 있을 수 없다" }
}

/** [value] 가 답에 그대로 돌아온다. */
data class DecisionChoice(val value: String, val description: String)

/** [label] 이 답에 그대로 돌아온다. */
data class DecisionLevel(val label: String, val description: String)
