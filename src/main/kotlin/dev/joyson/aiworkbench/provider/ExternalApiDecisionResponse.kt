package dev.joyson.aiworkbench.provider

data class ExternalApiDecisionResponse(
    val answers: List<DecisionAnswer>,
    override val usage: ProviderUsage? = null,
) : ExternalApiResponse

sealed interface DecisionAnswer {
    val name: String?

    data class Predicate(override val name: String?, val probability: Double) : DecisionAnswer
    data class Choice(override val name: String?, val choice: String, val confidence: Double?) : DecisionAnswer
    data class Score(override val name: String?, val score: Double, val confidence: Double?) : DecisionAnswer
    data class Refusal(override val name: String?) : DecisionAnswer
}
