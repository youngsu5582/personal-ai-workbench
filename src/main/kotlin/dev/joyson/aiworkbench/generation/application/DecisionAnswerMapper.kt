package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.DecisionAnswer
import dev.joyson.aiworkbench.provider.ExternalApiDecisionResponse

/**
 * Provider 포트가 준 답을 우리가 보관하는 답으로 옮긴다.
 *
 * [ProviderRequestMapper] 와 반대 방향이다. 두 어휘가 지금은 같은 모양이어도 타입을 나눠 두는 이유는
 * **행에 남는 모양은 남기는 모듈이 소유**하기 때문이다 — 포트가 바뀌어도 이미 저장된 답은 그대로 읽혀야 한다.
 */
object DecisionAnswerMapper {

    fun from(answers: List<ExternalApiDecisionResponse.Answer>): List<DecisionAnswer> = answers.map { answer ->
        when (answer) {
            is ExternalApiDecisionResponse.Answer.Predicate -> DecisionAnswer.Predicate(answer.name, answer.probability)
            is ExternalApiDecisionResponse.Answer.Choice -> DecisionAnswer.Choice(answer.name, answer.choice, answer.confidence)
            is ExternalApiDecisionResponse.Answer.Score -> DecisionAnswer.Score(answer.name, answer.score, answer.confidence)
            is ExternalApiDecisionResponse.Answer.Refusal -> DecisionAnswer.Refusal(answer.name)
        }
    }
}
