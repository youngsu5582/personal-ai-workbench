package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.DecisionAnswerResult
import dev.joyson.aiworkbench.generation.domain.DecisionTaskResult
import dev.joyson.aiworkbench.provider.DecisionAnswer
import dev.joyson.aiworkbench.provider.ExternalApiDecisionResponse

/** Provider의 판정 응답을 Job이 보관하는 결과로 옮긴다. */
object TaskResultMapper {
    fun from(response: ExternalApiDecisionResponse) = DecisionTaskResult(response.answers.map { answer -> when (answer) {
        is DecisionAnswer.Predicate -> DecisionAnswerResult.Predicate(answer.name, answer.probability)
        is DecisionAnswer.Choice -> DecisionAnswerResult.Choice(answer.name, answer.choice, answer.confidence)
        is DecisionAnswer.Score -> DecisionAnswerResult.Score(answer.name, answer.score, answer.confidence)
        is DecisionAnswer.Refusal -> DecisionAnswerResult.Refusal(answer.name)
    } })
}
