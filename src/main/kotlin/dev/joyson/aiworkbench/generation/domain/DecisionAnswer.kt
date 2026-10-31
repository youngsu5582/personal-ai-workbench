package dev.joyson.aiworkbench.generation.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * 질문 하나에 대한 답. 질문의 종류와 같은 갈래로 온다.
 *
 * 행(`generated_decisions.answers`)과 API 에 같은 모양으로 나간다. 판별자가 행에 그대로 들어가므로
 * 바꾸면 기존 행을 못 읽는다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DecisionAnswer.Predicate::class, name = "predicate"),
    JsonSubTypes.Type(value = DecisionAnswer.Choice::class, name = "choice"),
    JsonSubTypes.Type(value = DecisionAnswer.Score::class, name = "score"),
    JsonSubTypes.Type(value = DecisionAnswer.Refusal::class, name = "refusal"),
)
sealed interface DecisionAnswer {
    /** 답한 질문의 이름. 저쪽이 이름을 돌려주지 않으면 null 이다. */
    val name: String?

    data class Predicate(override val name: String?, val probability: Double) : DecisionAnswer

    data class Choice(override val name: String?, val choice: String, val confidence: Double?) : DecisionAnswer

    data class Score(override val name: String?, val score: Double, val confidence: Double?) : DecisionAnswer

    /** 모델이 이 질문에 답하기를 거절했다. Task 의 실패가 아니다 — 나머지 질문의 답은 쓸 수 있다. */
    data class Refusal(override val name: String?) : DecisionAnswer
}
