package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiDecisionRequest
import dev.joyson.aiworkbench.provider.ExternalApiDecisionRequest.Input
import dev.joyson.aiworkbench.provider.ExternalApiDecisionRequest.Option
import dev.joyson.aiworkbench.provider.ExternalApiDecisionRequest.Question
import dev.joyson.aiworkbench.provider.ExternalApiDecisionResponse
import dev.joyson.aiworkbench.provider.ExternalApiDecisionResponse.Answer
import dev.joyson.aiworkbench.provider.ExternalApiFileInput
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.http.HttpClient
import java.nio.file.Path
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **실제 OpenAI Decisions API 를 부른다.** 막는 방식과 돌리는 법은 [OpenAIProviderLiveTest] 와 같다.
 *
 * ```
 * OPENAI_LIVE=1 ./gradlew test --tests '*OpenAIDecisionProviderLiveTest*'
 * ```
 *
 * 문서로만 아는 것을 확인한다 — 경로와 모델 이름, 근거·질문의 모양([OpenAIDecisionRequest]),
 * 질문 종류마다 답이 어떤 값으로 오는지, 그리고 **`usage` 의 실제 생김새**.
 * 마지막은 같은 모델의 텍스트 단가표가 판정 호출에도 매겨지는지를 정한다.
 *
 * 호출마다 과금된다. 응답의 답과 사용량은 `build/live-test/` 에 남는다.
 */
@EnabledIf("dev.joyson.aiworkbench.provider.openai.OpenAIProviderLiveTest#liveEnabled")
class OpenAIDecisionProviderLiveTest {

    private val provider: OpenAIDecisionProvider by lazy {
        val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        val factory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(Duration.ofMinutes(1)) }
        OpenAIDecisionProvider(
            OpenAIDecisionClient(
                RestClient.builder()
                    .baseUrl("https://api.openai.com")
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${OpenAIProviderLiveTest.apiKey()!!}")
                    .requestFactory(factory)
                    .build(),
            ),
        )
    }

    private val model = "gpt-6-luna"

    /** 세 종류를 한 요청에 실어 각자 어떤 값으로 오는지 본다. */
    @Test
    fun `질문 종류마다 답을 받는다`() {
        val response = decide(
            ExternalApiDecisionRequest(
                input = listOf(Input.Text("다음 달부터 구독을 해지하고 싶어요. 오늘 안에 처리해 주세요.")),
                questions = listOf(
                    Question.Predicate("is_cancellation", "사용자가 구독 해지 의사를 밝혔는가?"),
                    Question.Choice(
                        "intent", "주된 의도를 고른다.",
                        listOf(Option("cancel", "해지·환불을 원한다"), Option("question", "정보를 묻는다")),
                    ),
                    Question.Score(
                        "urgency", "처리 긴급도를 평가한다.",
                        listOf(Option("low", "급하지 않다"), Option("high", "빨리 처리해야 한다")),
                    ),
                ),
            ),
            "live-decision.json",
        )

        assertEquals(3, response.answers.size, response.answers.toString())
        // 순서는 보장되지 않으므로 이름으로 짝을 짓는다.
        val byName = response.answers.associateBy { it.name }
        val predicate = assertIs<Answer.Predicate>(byName["is_cancellation"])
        assertTrue(predicate.probability in 0.0..1.0, "확률 범위가 아니다: ${predicate.probability}")
        assertEquals("cancel", assertIs<Answer.Choice>(byName["intent"]).choice)
        assertIs<Answer.Score>(byName["urgency"])
    }

    /** 이미지 근거를 바이트(data URL)로 실어 받아 주는지 본다. 빨간 정사각형 하나라 답이 분명하다. */
    @Test
    fun `이미지 근거를 받는다`() {
        val response = decide(
            ExternalApiDecisionRequest(
                input = listOf(
                    Input.Image(ExternalApiFileInput.Bytes(redSquare(), "image/png", "red.png")),
                    Input.Text("위 이미지를 보고 답한다."),
                ),
                questions = listOf(Question.Predicate("is_red", "이미지의 대부분이 빨간색인가?")),
            ),
            "live-decision-image.json",
        )

        val predicate = assertIs<Answer.Predicate>(response.answers.single())
        assertTrue(predicate.probability > 0.5, "빨간 이미지를 빨갛다고 보지 않았다: ${predicate.probability}")
    }

    private fun redSquare(): ByteArray {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { color = Color.RED; fillRect(0, 0, 64, 64); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun decide(request: ExternalApiDecisionRequest, fileName: String): ExternalApiDecisionResponse {
        val response = provider.decide(model, request)

        // 단가표의 경로와 대조할 수 있게 사용량을 남긴다.
        val usage = response.usage?.raw
        Path.of("build/live-test/$fileName").createParentDirectories().writeText(
            JsonMapper.shared().writerWithDefaultPrettyPrinter().writeValueAsString(
                mapOf("model" to model, "answers" to response.answers.map { it.toString() }, "usage" to usage),
            ),
        )
        println("model=$model answers=${response.answers} usage=$usage")

        assertTrue(usage?.containsKey("input_tokens") == true, "input_tokens 가 없다: $usage")
        // 판정은 출력 토큰이 없다. 생기면 같은 모델의 텍스트 단가표가 판정에도 출력 단가를 매긴다.
        assertEquals(0, (usage?.get("output_tokens") as? Number)?.toInt() ?: 0, "출력 토큰이 생겼다: $usage")
        return response
    }
}
