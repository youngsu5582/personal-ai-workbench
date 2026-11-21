package dev.joyson.aiworkbench.provider.gemini

import dev.joyson.aiworkbench.provider.ExternalApiTextRequest
import dev.joyson.aiworkbench.provider.ExternalApiTextResponse
import dev.joyson.aiworkbench.provider.ReasoningEffort
import dev.joyson.aiworkbench.provider.StopReason
import org.junit.jupiter.api.condition.EnabledIf
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
 * **실제 Gemini API 를 부른다.** 막는 방식은 OpenAI live 테스트와 같다 — 두 겹이다.
 *
 * 1. `GEMINI_LIVE=1` 을 명시해야 돈다. 키가 있는 것은 앱을 띄우기 위한 상태지 불러도 좋다는 뜻이 아니다.
 * 2. 프로젝트 전용 이름 `WORKBENCH_PROVIDER_GEMINI_API_KEY` 만 읽는다(환경변수 → `.env`). 공용 `GEMINI_API_KEY` 는 읽지 않는다.
 *
 * ```
 * GEMINI_LIVE=1 ./gradlew test --tests '*GeminiTextProviderLiveTest*'
 * GEMINI_LIVE=1 GEMINI_TEXT_MODEL=gemini-3.6-flash ./gradlew test --tests '*GeminiTextProviderLiveTest*'
 * ```
 *
 * 문서로만 아는 것을 확인한다 — 경로 · 헤더 · `thinkingLevel` 표기, 생각 요약이 답과 따로 오는지,
 * 스키마의 키 순서대로 답하는지, 그리고 **`usageMetadata` 의 실제 생김새**. 마지막은 단가표가 가리키는 경로다.
 *
 * 키의 프로젝트에 결제가 연결돼 있으면 호출마다 과금된다. 기본 모델은 더 싼 `gemini-3.5-flash-lite` 이고,
 * 응답의 사용량은 `build/live-test/` 에 남는다.
 */
@EnabledIf("liveEnabled")
class GeminiTextProviderLiveTest {

    private val provider: GeminiTextProvider by lazy {
        val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        val factory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(Duration.ofMinutes(3)) }
        GeminiTextProvider(
            GeminiTextClient(
                RestClient.builder()
                    .baseUrl("https://generativelanguage.googleapis.com")
                    .defaultHeader(GeminiTextClient.API_KEY_HEADER, apiKey()!!)
                    .requestFactory(factory)
                    .build(),
            ),
        )
    }

    private val model: String get() = System.getenv("GEMINI_TEXT_MODEL") ?: "gemini-3.5-flash-lite"

    @Test
    fun `형식 없는 글을 받는다`() {
        val response = generate(
            ExternalApiTextRequest(prompt = "Say hello in Korean, in one short sentence.", instructions = "Answer briefly."),
            "live-gemini-plain.json",
        )

        assertEquals(StopReason.COMPLETED, response.stopReason)
        assertTrue(response.text.isNotBlank())
    }

    /** 키 순서가 지켜지는지가 이 기능의 전제다. `reasoning` 이 짧은 `answer` 보다 앞에 오는지 본다. */
    @Test
    fun `스키마의 키 순서대로 JSON 을 받는다`() {
        val schema = """{"type":"object","properties":{"reasoning":{"type":"string"},"answer":{"type":"integer"}},""" +
            """"required":["reasoning","answer"]}"""

        val response = generate(
            ExternalApiTextRequest(prompt = "What is 6 times 7?", jsonSchema = schema),
            "live-gemini-schema.json",
        )

        assertEquals(StopReason.COMPLETED, response.stopReason)
        val answer = JsonMapper.shared().readTree(response.text)
        assertEquals(listOf("reasoning", "answer"), answer.propertyNames().toList(), response.text)
        assertEquals(42, answer["answer"].asInt())
    }

    /** 가장 얕게 생각시킨다. thinking 칸이 0 이라 빠지는지가 단가표의 `zeroIfAbsent` 근거다. */
    @Test
    fun `가장 얕은 effort 로 부를 수 있다`() {
        val response = generate(
            ExternalApiTextRequest(prompt = "What is 6 times 7? Answer with the number only.", effort = ReasoningEffort.MINIMAL),
            "live-gemini-effort-minimal.json",
        )

        assertEquals(StopReason.COMPLETED, response.stopReason)
        assertTrue(response.text.contains("42"), response.text)
    }

    /** 깊게 생각시킨다. thinking 칸이 실제 이름으로 오는지 본다 — 단가표가 그 이름에 출력 단가를 매긴다. */
    @Test
    fun `깊은 effort 에서는 thinking 토큰 칸이 온다`() {
        val response = generate(
            ExternalApiTextRequest(prompt = "How many prime numbers are there below 50? Answer with the number only.", effort = ReasoningEffort.HIGH),
            "live-gemini-effort-high.json",
        )

        assertEquals(StopReason.COMPLETED, response.stopReason)
        assertTrue(response.usage?.raw?.containsKey("thoughtsTokenCount") == true, "thinking 칸이 없다: ${response.usage?.raw}")
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

        // 단가표가 엄격하게 보는 칸과, 답이 있으면 오는 칸이다.
        assertTrue(usage?.containsKey("promptTokenCount") == true, "단가표가 가리키는 promptTokenCount 가 없다: $usage")
        assertTrue(usage?.containsKey("candidatesTokenCount") == true, "단가표가 가리키는 candidatesTokenCount 가 없다: $usage")
        return response
    }

    companion object {
        @JvmStatic
        fun apiKey(): String? =
            System.getenv("WORKBENCH_PROVIDER_GEMINI_API_KEY")
                ?: dotEnv("WORKBENCH_PROVIDER_GEMINI_API_KEY")

        @JvmStatic
        fun liveEnabled(): Boolean =
            System.getenv("GEMINI_LIVE") == "1" && !apiKey().isNullOrBlank()

        private fun dotEnv(key: String): String? =
            Path.of(".env").toFile()
                .takeIf { it.isFile }
                ?.readLines()
                ?.firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("=")
                ?.trim()
                ?.trim('"')
    }
}
