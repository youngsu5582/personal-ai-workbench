package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiTextProvider
import dev.joyson.aiworkbench.provider.ExternalApiTextRequest
import dev.joyson.aiworkbench.provider.ExternalApiTextResponse
import dev.joyson.aiworkbench.provider.FailureKind
import dev.joyson.aiworkbench.provider.ProviderUsage
import dev.joyson.aiworkbench.provider.ReasoningEffort
import dev.joyson.aiworkbench.provider.StopReason
import org.slf4j.LoggerFactory

/**
 * 표준 글 생성 요청을 OpenAI Responses API 의 어휘로 옮기는 어댑터.
 *
 * 하는 일은 이미지 어댑터와 같은 갈래다 — 모델 확인, 요청 번역, 실패 번역([OpenAIFailures]),
 * `usage` 를 해석하지 않고 싣기. 여기에만 있는 것은 **응답이 왔는데 쓸 수 없는 경우**를 가르는 일이다.
 * 그 경우들은 예외가 아니라 [StopReason] 으로 답한다 — 과금된 응답이라 사용량을 버리면 안 된다.
 */
class OpenAITextProvider(
    private val client: OpenAITextClient,
) : ExternalApiTextProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = OpenAIFailures.PROVIDER_NAME

    /** 사실 질의다. 모르는 모델이면 예외가 아니라 false. */
    override fun supports(model: String): Boolean = OpenAITextModel.find(model) != null

    override fun generateText(
        model: String,
        request: ExternalApiTextRequest,
    ): ExternalApiTextResponse = OpenAIFailures.translate(log, model) {
        // supports 를 건너뛰고 부를 수 있으므로 여기서도 확인한다. 다시 보내도 달라지지 않으니 재시도 대상이 아니다.
        val target = OpenAITextModel.find(model)
            ?: throw ExternalApiException(FailureKind.REJECTED, "다루지 않는 모델이다: $model")

        val response = client.respond(
            OpenAIResponseRequest(
                model = target.modelName,
                input = request.prompt,
                instructions = request.instructions,
                text = request.jsonSchema?.let { OpenAITextOptions(OpenAIJsonSchemaFormat(name = SCHEMA_NAME, schema = it)) },
                reasoning = request.effort?.let { OpenAIReasoning(effortOf(it)) },
            ),
        )

        assemble(response)
    }

    /**
     * 응답을 포트 타입으로 옮긴다.
     *
     * 응답이 왔어도 **저쪽 처리가 실패한 것**(`failed`)과 **모르는 상태**는 예외다. 앞쪽은 다시 해볼 만하고,
     * 뒤쪽은 원인을 모르니 다시 하지 않는다. 끝까지 못 간 것(`incomplete`)과 거절은 예외가 아니다.
     */
    private fun assemble(response: OpenAIResponse): ExternalApiTextResponse {
        val contents = response.output.orEmpty()
            .filter { it.type == MESSAGE }
            .flatMap { it.content.orEmpty() }
        val text = contents.filter { it.type == OUTPUT_TEXT }.mapNotNull { it.text }.joinToString("")

        val (stopReason, body) = when (response.status) {
            COMPLETED -> {
                val refusal = contents.firstOrNull { it.type == REFUSAL }?.refusal
                when {
                    refusal != null -> StopReason.REFUSED to refusal
                    // 200 에 다 됐다는데 글이 없는 건 설명되지 않는다. 이미지 쪽의 "이미지 없는 200" 과 같이
                    // 다시 받으면 멀쩡할 수 있는 저쪽 사정으로 본다.
                    text.isEmpty() -> throw ExternalApiException(FailureKind.PROVIDER_ERROR, "응답에 글이 없다")
                    else -> StopReason.COMPLETED to text
                }
            }

            INCOMPLETE -> stopReasonOf(response.incompleteDetails?.reason) to text

            FAILED -> throw ExternalApiException(
                FailureKind.PROVIDER_ERROR,
                "[${OpenAIFailures.PROVIDER_NAME} 응답 실패${response.error?.code?.let { " $it" }.orEmpty()}] " +
                    (response.error?.message ?: "사유가 없다"),
            )

            // 동기로 불렀으니 queued · in_progress 는 올 수 없고, 취소한 적도 없다.
            else -> throw ExternalApiException(FailureKind.UNKNOWN, "설명되지 않는 응답 상태다: ${response.status}")
        }

        return ExternalApiTextResponse(
            text = body,
            stopReason = stopReason,
            // OpenAI 는 토큰만 말하고 비용은 말하지 않는다. 달러로 바꾸는 것은 단가를 아는 쪽의 일이다.
            usage = response.usage?.let { ProviderUsage(raw = it) },
            providerId = response.id,
        )
    }

    /** 모르는 이유로 멈췄으면 무엇이 잘렸는지 말할 수 없다. 모르는 채로 다시 하지 않는다. */
    private fun stopReasonOf(reason: String?): StopReason = when (reason) {
        "max_output_tokens" -> StopReason.MAX_TOKENS
        "content_filter" -> StopReason.CONTENT_FILTER
        else -> throw ExternalApiException(FailureKind.UNKNOWN, "알 수 없는 이유로 멈췄다: $reason")
    }

    /** 지금은 값이 1:1 로 대응한다. `minimal` 은 우리 모델 중 받는 것이 없어 눈금에 두지 않았다. */
    private fun effortOf(effort: ReasoningEffort): String = when (effort) {
        ReasoningEffort.NONE -> "none"
        ReasoningEffort.LOW -> "low"
        ReasoningEffort.MEDIUM -> "medium"
        ReasoningEffort.HIGH -> "high"
        ReasoningEffort.XHIGH -> "xhigh"
        ReasoningEffort.MAX -> "max"
    }

    private companion object {
        /** 이름은 저쪽이 요구할 뿐 우리에게 뜻이 없다. 받는 쪽마다 짓게 하면 지을 규칙만 하나 늘어난다. */
        const val SCHEMA_NAME = "output"

        const val COMPLETED = "completed"
        const val INCOMPLETE = "incomplete"
        const val FAILED = "failed"

        const val MESSAGE = "message"
        const val OUTPUT_TEXT = "output_text"
        const val REFUSAL = "refusal"
    }
}
