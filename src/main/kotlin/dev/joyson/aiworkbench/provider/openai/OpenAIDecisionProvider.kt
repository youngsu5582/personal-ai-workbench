package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiDecisionProvider
import dev.joyson.aiworkbench.provider.ExternalApiDecisionRequest
import dev.joyson.aiworkbench.provider.ExternalApiDecisionResponse
import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiFileInput
import dev.joyson.aiworkbench.provider.FailureKind
import dev.joyson.aiworkbench.provider.ProviderUsage
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * 표준 판정 요청을 OpenAI Decisions API 의 어휘로 옮기는 어댑터.
 *
 * 하는 일은 텍스트 어댑터와 같은 갈래다 — 모델 확인, 요청 번역, 실패 번역([OpenAIFailures]),
 * `usage` 를 해석하지 않고 싣기. 답을 옮기다 나는 실패도 [OpenAIFailures.translate] 안에서 나므로
 * 호출이 성공한 뒤의 실패가 거절(과금 없음)로 적히지 않는다.
 */
class OpenAIDecisionProvider(
    private val client: OpenAIDecisionClient,
) : ExternalApiDecisionProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = OpenAIFailures.PROVIDER_NAME

    /** 사실 질의다. 모르는 모델이면 예외가 아니라 false. */
    override fun supports(model: String): Boolean = OpenAIDecisionModel.find(model) != null

    override fun decide(
        model: String,
        request: ExternalApiDecisionRequest,
    ): ExternalApiDecisionResponse = OpenAIFailures.translate(log, model) {
        // supports 를 건너뛰고 부를 수 있으므로 여기서도 확인한다. 다시 보내도 달라지지 않으니 재시도 대상이 아니다.
        val target = OpenAIDecisionModel.find(model)
            ?: throw ExternalApiException(FailureKind.REJECTED, "다루지 않는 모델이다: $model")

        val response = client.decide(
            OpenAIDecisionRequest(
                model = target.modelName,
                input = listOf(OpenAIDecisionMessage(content = request.input.map(::contentOf))),
                questions = request.questions.map(::questionOf),
            ),
        )

        // 200 인데 답이 없는 건 이미지 쪽의 "이미지 없는 200" 과 같다. 다시 받으면 멀쩡할 수 있는 저쪽 사정으로 본다.
        if (response.answers.isEmpty()) throw ExternalApiException(FailureKind.PROVIDER_ERROR, "응답에 답이 없다")

        ExternalApiDecisionResponse(
            answers = response.answers.map(::answerOf),
            // OpenAI 는 토큰만 말하고 비용은 말하지 않는다. 달러로 바꾸는 것은 단가를 아는 쪽의 일이다.
            usage = response.usage?.let { ProviderUsage(raw = it) },
            providerId = response.id,
        )
    }

    private fun contentOf(input: ExternalApiDecisionRequest.Input): OpenAIDecisionContent = when (input) {
        is ExternalApiDecisionRequest.Input.Text -> OpenAIDecisionContent.Text(input.text)
        is ExternalApiDecisionRequest.Input.Image -> OpenAIDecisionContent.Image(
            when (val image = input.image) {
                is ExternalApiFileInput.Url -> image.url.toString()
                is ExternalApiFileInput.Bytes ->
                    "data:${image.mimeType};base64,${Base64.getEncoder().encodeToString(image.bytes)}"
            },
        )
    }

    private fun questionOf(question: ExternalApiDecisionRequest.Question): OpenAIDecisionQuestion = when (question) {
        is ExternalApiDecisionRequest.Question.Predicate ->
            OpenAIDecisionQuestion.Predicate(question.name, question.instructions)

        is ExternalApiDecisionRequest.Question.Choice -> OpenAIDecisionQuestion.Choice(
            question.name,
            question.instructions,
            question.choices.map { OpenAIDecisionChoice(it.value, it.description) },
        )

        is ExternalApiDecisionRequest.Question.Score -> OpenAIDecisionQuestion.Score(
            question.name,
            question.instructions,
            question.levels.map { OpenAIDecisionLevel(it.value, it.description) },
        )
    }

    /**
     * 모르는 답 종류나 빠진 값은 예외다. [OpenAIFailures.translate] 가 알 수 없는 실패로 바꿔 다시 하지 않는다 —
     * 저쪽이 답의 모양을 바꾼 것이면 다시 받아도 같다.
     */
    private fun answerOf(answer: OpenAIDecisionAnswer): ExternalApiDecisionResponse.Answer = when (answer.type) {
        "predicate" -> ExternalApiDecisionResponse.Answer.Predicate(answer.name, requireNotNull(answer.probability) { "probability 가 없다" })
        "choice" -> ExternalApiDecisionResponse.Answer.Choice(answer.name, requireNotNull(answer.choice) { "choice 가 없다" }, answer.confidence)
        "score" -> ExternalApiDecisionResponse.Answer.Score(answer.name, requireNotNull(answer.score) { "score 가 없다" }, answer.confidence)
        "refusal" -> ExternalApiDecisionResponse.Answer.Refusal(answer.name)
        else -> error("모르는 답 종류다: ${answer.type}")
    }
}
