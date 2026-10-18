package dev.joyson.aiworkbench.provider.openai

import org.springframework.web.client.RestClient

/**
 * OpenAI Decisions API의 HTTP 계약만 안다. 실패 처리는 [exchange] 로 Images API 와 공유한다.
 *
 * **미검증.** 요청·응답 본문의 모양(`input[].content[]`·`questions[]`·`answers[]`)은 실제 API 로
 * 확인하지 않았다. 확인하는 자리는 `OpenAIProviderLiveTest.decisionsApiAcceptsText` 다.
 */
class OpenAIDecisionClient(private val restClient: RestClient) {
    fun decide(request: OpenAIDecisionRequest): OpenAIDecisionResponse =
        exchange(restClient.post().uri("/v1/decisions").body(request), OpenAIDecisionResponse::class.java)
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
