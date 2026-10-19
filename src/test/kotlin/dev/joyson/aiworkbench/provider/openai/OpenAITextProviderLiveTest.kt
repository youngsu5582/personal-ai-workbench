package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiTextRequest
import dev.joyson.aiworkbench.provider.ExternalApiTextResponse
import dev.joyson.aiworkbench.provider.ReasoningEffort
import dev.joyson.aiworkbench.provider.StopReason
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **실제 OpenAI Responses API 를 부른다.** 막는 방식과 돌리는 법은 [OpenAIProviderLiveTest] 와 같다.
 *
 * ```
 * OPENAI_LIVE=1 ./gradlew test --tests '*OpenAITextProviderLiveTest*'
 * OPENAI_LIVE=1 OPENAI_TEXT_MODEL=gpt-6.1-sol ./gradlew test --tests '*OpenAITextProviderLiveTest*'
 * ```
 *
 * 문서로만 아는 것을 확인한다 — 경로와 모델 이름, `text.format` 의 모양, `store: false` 를 받아주는지,
 * 응답에서 글이 `output[].content[]` 의 어디에 오는지, 스키마의 키 순서대로 답하는지,
 * 그리고 **`usage` 의 실제 생김새**. 마지막은 단가표가 가리키는 경로라 틀리면 비용이 전부 "모름" 이 된다.
 *
 * 호출마다 과금된다. 기본 모델은 가장 싼 `gpt-6-luna` 이고, 응답의 사용량은 `build/live-test/` 에 남는다.
 */
@EnabledIf("dev.joyson.aiworkbench.provider.openai.OpenAIProviderLiveTest#liveEnabled")
class OpenAITextProviderLiveTest {

    private val provider: OpenAITextProvider by lazy {
        val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        val factory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(Duration.ofMinutes(3)) }
        OpenAITextProvider(
            OpenAITextClient(
                RestClient.builder()
                    .baseUrl("https://api.openai.com")
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${OpenAIProviderLiveTest.apiKey()!!}")
                    .requestFactory(factory)
                    .build(),
            ),
        )
    }

    private val model: String get() = System.getenv("OPENAI_TEXT_MODEL") ?: "gpt-6-luna"

    @Test
    fun `형식 없는 글을 받는다`() {
        val response = generate(
            ExternalApiTextRequest(prompt = "Say hello in Korean, in one short sentence.", instructions = "Answer briefly."),
            "live-text-plain.json",
        )

        assertEquals(StopReason.COMPLETED, response.stopReason)
        assertTrue(response.text.isNotBlank())
    }

    /** 키 순서가 지켜지는지가 이 기능의 전제다. `reasoning` 이 짧은 `answer` 보다 앞에 오는지 본다. */
    @Test
    fun `스키마의 키 순서대로 JSON 을 받는다`() {
        val schema = """{"type":"object","properties":{"reasoning":{"type":"string"},"answer":{"type":"integer"}},""" +
            """"required":["reasoning","answer"],"additionalProperties":false}"""

        val response = generate(
            ExternalApiTextRequest(prompt = "What is 6 times 7?", jsonSchema = schema),
            "live-text-schema.json",
        )

        assertEquals(StopReason.COMPLETED, response.stopReason)
        val answer = JsonMapper.shared().readTree(response.text)
        assertEquals(listOf("reasoning", "answer"), answer.propertyNames().toList(), response.text)
        assertEquals(42, answer["answer"].asInt())
    }

    /** effort 를 받아주는지, 그리고 낮추면 추론 토큰이 줄어드는지 본다. 어느 값을 받는지는 모델마다 다르다. */
    @Test
    fun `effort 를 낮춰 부를 수 있다`() {
        val response = generate(
            ExternalApiTextRequest(prompt = "What is 6 times 7? Answer with the number only.", effort = ReasoningEffort.LOW),
            "live-text-effort-low.json",
        )

        assertEquals(StopReason.COMPLETED, response.stopReason)
        assertTrue(response.text.contains("42"), response.text)
    }

    private fun generate(request: ExternalApiTextRequest, fileName: String): ExternalApiTextResponse {
        val response = provider.generateText(model, request)

        // 단가표의 경로가 맞는지 눈으로 대조할 수 있게 사용량을 남긴다.
        val usage = response.usage?.raw
        Path.of("build/live-test/$fileName").createParentDirectories().writeText(
            JsonMapper.shared().writerWithDefaultPrettyPrinter().writeValueAsString(
                mapOf("model" to model, "stopReason" to response.stopReason, "text" to response.text, "usage" to usage),
            ),
        )
        println("model=$model stop=${response.stopReason} usage=$usage text=${response.text}")

        assertTrue(usage?.containsKey("input_tokens") == true, "단가표가 가리키는 input_tokens 가 없다: $usage")
        assertTrue(usage?.containsKey("output_tokens") == true, "단가표가 가리키는 output_tokens 가 없다: $usage")
        return response
    }
}
