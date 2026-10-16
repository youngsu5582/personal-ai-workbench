package dev.joyson.aiworkbench.provider.openai

import org.springframework.http.HttpStatusCode
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

/** OpenAI Decisions API의 HTTP 계약만 안다. */
class OpenAIDecisionClient(private val restClient: RestClient) {
    private val mapper = jacksonObjectMapper()

    fun decide(request: OpenAIDecisionRequest): OpenAIDecisionResponse =
        restClient.post().uri("/v1/decisions").body(request).exchange { _, response ->
            if (response.statusCode.is2xxSuccessful) {
                response.bodyTo(OpenAIDecisionResponse::class.java)
                    ?: throw OpenAIException(response.statusCode, "응답 본문이 비어 있다")
            } else {
                val raw = runCatching { response.bodyTo(String::class.java) }.getOrNull()
                val error = raw?.let { runCatching { mapper.readValue(it, OpenAIErrorEnvelope::class.java).error }.getOrNull() }
                throw OpenAIException(
                    status = response.statusCode,
                    detail = error?.message ?: raw?.take(500)?.ifBlank { null } ?: "본문이 비어 있다",
                    code = error?.code,
                )
            }
        }
}

data class OpenAIDecisionRequest(
    val model: String,
    val input: List<OpenAIDecisionMessage>,
    val questions: List<OpenAIDecisionQuestion>,
)

data class OpenAIDecisionMessage(val role: String = "user", val content: List<OpenAIDecisionInput>)

sealed interface OpenAIDecisionInput { val type: String }
data class OpenAIDecisionText(override val type: String = "input_text", val text: String) : OpenAIDecisionInput
data class OpenAIDecisionImage(override val type: String = "input_image", val image_url: String) : OpenAIDecisionInput

sealed interface OpenAIDecisionQuestion { val type: String; val name: String; val instructions: String }
data class OpenAIPredicateQuestion(
    override val type: String = "predicate", override val name: String, override val instructions: String,
) : OpenAIDecisionQuestion
data class OpenAIChoiceQuestion(
    override val type: String = "choice", override val name: String, override val instructions: String, val choices: List<OpenAIChoice>,
) : OpenAIDecisionQuestion
data class OpenAIScoreQuestion(
    override val type: String = "score", override val name: String, override val instructions: String, val levels: List<OpenAIScoreLevel>,
) : OpenAIDecisionQuestion
data class OpenAIChoice(val value: String, val description: String)
data class OpenAIScoreLevel(val label: String, val description: String)

data class OpenAIDecisionResponse(val answers: List<OpenAIDecisionAnswer> = emptyList(), val usage: Map<String, Any?>? = null)
data class OpenAIDecisionAnswer(
    val type: String,
    val name: String? = null,
    val probability: Double? = null,
    val choice: String? = null,
    val confidence: Double? = null,
    val score: Double? = null,
)
