package dev.joyson.aiworkbench.generation.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(JsonSubTypes.Type(value = DecisionTaskResult::class, name = "decision"))
sealed interface TaskResult

data class DecisionTaskResult(val answers: List<DecisionAnswerResult>) : TaskResult

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DecisionAnswerResult.Predicate::class, name = "predicate"),
    JsonSubTypes.Type(value = DecisionAnswerResult.Choice::class, name = "choice"),
    JsonSubTypes.Type(value = DecisionAnswerResult.Score::class, name = "score"),
    JsonSubTypes.Type(value = DecisionAnswerResult.Refusal::class, name = "refusal"),
)
sealed interface DecisionAnswerResult {
    val name: String?
    data class Predicate(override val name: String?, val probability: Double) : DecisionAnswerResult
    data class Choice(override val name: String?, val choice: String, val confidence: Double?) : DecisionAnswerResult
    data class Score(override val name: String?, val score: Double, val confidence: Double?) : DecisionAnswerResult
    data class Refusal(override val name: String?) : DecisionAnswerResult
}
