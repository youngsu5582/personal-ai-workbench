package dev.joyson.aiworkbench.provider.gemini

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
 * 표준 글 생성 요청을 Gemini `generateContent` 의 어휘로 옮기는 어댑터.
 *
 * 하는 일은 OpenAI 텍스트 어댑터와 같은 갈래다 — 모델 확인, 요청 번역, 실패 번역([GeminiFailures]),
 * 사용량을 해석하지 않고 싣기, 응답이 왔는데 쓸 수 없는 경우를 예외가 아니라 [StopReason] 으로 답하기.
 *
 * Gemini 에는 거절 전용 칸이 없어 [StopReason.REFUSED] 는 나오지 않는다. 거절은 글로 오거나 필터로 온다.
 */
class GeminiTextProvider(
    private val client: GeminiTextClient,
) : ExternalApiTextProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = GeminiFailures.PROVIDER_NAME

    override val models: Set<String> = GeminiTextModel.entries.map { it.modelName }.toSet()

    /** `thinkingLevel` 이 표현하는 넷이다. 생각을 끄는 값과 high 보다 깊은 값은 없다. */
    override val efforts: Set<ReasoningEffort> = ReasoningEffort.entries.filter { thinkingLevelOf(it) != null }.toSet()

    override fun generateText(
        model: String,
        request: ExternalApiTextRequest,
    ): ExternalApiTextResponse = GeminiFailures.translate(log, model) {
        // supports · efforts 를 건너뛰고 부를 수 있으므로 여기서도 확인한다. 다시 보내도 같으니 재시도 대상이 아니다.
        val target = GeminiTextModel.find(model)
            ?: throw ExternalApiException(FailureKind.REJECTED, "다루지 않는 모델이다: $model")
        val thinkingLevel = request.effort?.let {
            thinkingLevelOf(it) ?: throw ExternalApiException(FailureKind.REJECTED, "Gemini 는 effort=$it 를 표현하지 못한다")
        }

        val response = client.generateContent(
            target.modelName,
            GeminiGenerateContentRequest(
                contents = listOf(GeminiContent(role = USER, parts = listOf(GeminiPart(text = request.prompt)))),
                systemInstruction = request.instructions?.let { GeminiContent(parts = listOf(GeminiPart(text = it))) },
                generationConfig = generationConfigOf(request.jsonSchema, thinkingLevel),
            ),
        )

        assemble(response)
    }

    /** 바꿀 것이 없으면 칸째 보내지 않는다 — 모델 기본값을 우리가 지어내지 않는다. */
    private fun generationConfigOf(jsonSchema: String?, thinkingLevel: String?): GeminiGenerationConfig? {
        if (jsonSchema == null && thinkingLevel == null) return null
        return GeminiGenerationConfig(
            responseMimeType = jsonSchema?.let { JSON },
            responseJsonSchema = jsonSchema,
            thinkingConfig = thinkingLevel?.let { GeminiThinkingConfig(it) },
        )
    }

    /**
     * 응답을 포트 타입으로 옮긴다.
     *
     * 프롬프트가 막혔으면 후보가 없다 — 필터에 걸린 것으로 답한다. 막히지도 않았는데 후보가 없거나,
     * 다 됐다면서 글이 없는 것은 설명되지 않아 다시 받으면 멀쩡할 수 있는 저쪽 사정으로 본다.
     */
    private fun assemble(response: GeminiGenerateContentResponse): ExternalApiTextResponse {
        val usage = response.usageMetadata?.let { ProviderUsage(raw = it) }

        response.promptFeedback?.blockReason?.let { reason ->
            log.warn("프롬프트가 막혔다. reason={} id={}", reason, response.responseId)
            return ExternalApiTextResponse(text = "", stopReason = StopReason.CONTENT_FILTER, usage = usage, providerId = response.responseId)
        }

        val candidate = response.candidates?.firstOrNull()
            ?: throw ExternalApiException(FailureKind.PROVIDER_ERROR, "응답에 후보가 없다")
        // 생각의 요약은 답이 아니다. 섞으면 스키마를 준 답이 JSON 이 아니게 된다.
        val text = candidate.content?.parts.orEmpty().filter { it.thought != true }.mapNotNull { it.text }.joinToString("")

        val stopReason = when (val reason = candidate.finishReason) {
            STOP -> if (text.isEmpty()) throw ExternalApiException(FailureKind.PROVIDER_ERROR, "응답에 글이 없다") else StopReason.COMPLETED
            MAX_TOKENS -> StopReason.MAX_TOKENS
            in FILTERED -> StopReason.CONTENT_FILTER
            // 이유는 Google 이 늘려 간다. 예외로 바꾸면 과금된 응답의 글 · 사용량 · 식별자가 함께 버려진다.
            else -> {
                log.warn("응답이 다른 이유로 끝까지 가지 못했다. reason={} id={}", reason, response.responseId)
                StopReason.INCOMPLETE
            }
        }

        return ExternalApiTextResponse(text = text, stopReason = stopReason, usage = usage, providerId = response.responseId)
    }

    private companion object {
        const val USER = "user"
        const val JSON = "application/json"

        const val STOP = "STOP"
        const val MAX_TOKENS = "MAX_TOKENS"

        /** 내용 때문에 멈춘 이유들. `RECITATION` 은 저작물을 그대로 옮기려다 막힌 것이다. */
        val FILTERED = setOf("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII")

        /** 포트 눈금을 `thinkingLevel` 로 옮긴다. 표현할 말이 없으면 null 이다. */
        fun thinkingLevelOf(effort: ReasoningEffort): String? = when (effort) {
            ReasoningEffort.MINIMAL -> "MINIMAL"
            ReasoningEffort.LOW -> "LOW"
            ReasoningEffort.MEDIUM -> "MEDIUM"
            ReasoningEffort.HIGH -> "HIGH"
            ReasoningEffort.NONE, ReasoningEffort.XHIGH, ReasoningEffort.MAX -> null
        }
    }
}
