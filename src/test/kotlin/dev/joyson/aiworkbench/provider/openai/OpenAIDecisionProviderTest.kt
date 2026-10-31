package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiDecisionRequest
import dev.joyson.aiworkbench.provider.ExternalApiDecisionRequest.Input
import dev.joyson.aiworkbench.provider.ExternalApiDecisionRequest.Option
import dev.joyson.aiworkbench.provider.ExternalApiDecisionRequest.Question
import dev.joyson.aiworkbench.provider.ExternalApiDecisionResponse.Answer
import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiFileInput
import dev.joyson.aiworkbench.provider.FailureKind
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenAIDecisionProviderTest {

    private val builder = RestClient.builder().baseUrl(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val provider = OpenAIDecisionProvider(OpenAIDecisionClient(builder.build()))

    private val request = ExternalApiDecisionRequest(
        input = listOf(Input.Text("환불하고 싶어요")),
        questions = listOf(Question.Predicate("needs_review", "사람 확인이 필요한가?")),
    )

    private fun respondWith(body: String) {
        server.expect(requestTo("$BASE_URL${OpenAIDecisionClient.DECISIONS_PATH}"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `판정 모델만 지원한다고 답한다`() {
        assertTrue(provider.supports("gpt-6-luna"))
        assertFalse(provider.supports("gpt-image-2"))
        // 사실 질의라 모르는 모델에도 예외를 던지지 않는다.
        assertFalse(provider.supports(""))
    }

    /** 근거는 받은 순서대로 user 메시지 하나에 싣는다. 바이트 이미지는 data URL 이 된다. */
    @Test
    fun `근거와 질문을 받은 순서대로 싣는다`() {
        val image = "근거 이미지".toByteArray()
        server.expect(requestTo("$BASE_URL${OpenAIDecisionClient.DECISIONS_PATH}"))
            .andExpect(jsonPath("$.model").value("gpt-6-luna"))
            .andExpect(jsonPath("$.input[0].role").value("user"))
            .andExpect(jsonPath("$.input[0].content[0].type").value("input_text"))
            .andExpect(jsonPath("$.input[0].content[0].text").value("파손됐어요"))
            .andExpect(jsonPath("$.input[0].content[1].type").value("input_image"))
            .andExpect(jsonPath("$.input[0].content[1].image_url").value("data:image/png;base64,${Base64.getEncoder().encodeToString(image)}"))
            .andExpect(jsonPath("$.questions[0].type").value("predicate"))
            .andExpect(jsonPath("$.questions[1].type").value("choice"))
            .andExpect(jsonPath("$.questions[1].choices[0].value").value("damage"))
            .andExpect(jsonPath("$.questions[2].type").value("score"))
            .andExpect(jsonPath("$.questions[2].levels[1].label").value("high"))
            .andRespond(withSuccess("""{"answers":[{"type":"refusal","name":"needs_review"}]}""", MediaType.APPLICATION_JSON))

        provider.decide(
            "gpt-6-luna",
            ExternalApiDecisionRequest(
                input = listOf(
                    Input.Text("파손됐어요"),
                    Input.Image(ExternalApiFileInput.Bytes(image, "image/png", "evidence.png")),
                ),
                questions = listOf(
                    Question.Predicate("needs_review", "사람 확인이 필요한가?"),
                    Question.Choice("kind", "무슨 문제인가", listOf(Option("damage", "파손"), Option("other", "기타"))),
                    Question.Score("urgency", "얼마나 급한가", listOf(Option("low", "낮음"), Option("high", "높음"))),
                ),
            ),
        )

        server.verify()
    }

    @Test
    fun `답과 사용량과 식별자를 꺼낸다`() {
        respondWith(
            """{"id":"dec_1","answers":[
                 {"type":"predicate","name":"needs_review","probability":0.9},
                 {"type":"choice","name":"kind","choice":"damage","confidence":0.98},
                 {"type":"score","name":"urgency","score":0.75,"confidence":0.8},
                 {"type":"refusal","name":"other"}
               ],"usage":{"input_tokens":42}}""",
        )

        val response = provider.decide("gpt-6-luna", request)

        assertEquals(
            listOf(
                Answer.Predicate("needs_review", 0.9),
                Answer.Choice("kind", "damage", 0.98),
                Answer.Score("urgency", 0.75, 0.8),
                // 거절도 답의 하나다. 저쪽은 처리했고 과금했다.
                Answer.Refusal("other"),
            ),
            response.answers,
        )
        assertEquals(mapOf("input_tokens" to 42), response.usage?.raw)
        assertEquals("dec_1", response.providerId)
    }

    @Test
    fun `200 인데 답이 없으면 저쪽 사정으로 본다`() {
        respondWith("""{"answers":[]}""")

        val ex = assertFailsWith<ExternalApiException> { provider.decide("gpt-6-luna", request) }

        // 저쪽이 처리한 뒤라 과금 여부를 0 으로 단정하면 안 된다.
        assertEquals(FailureKind.PROVIDER_ERROR, ex.kind)
    }

    /** 호출은 이미 성공했다. 과금이 없다는 뜻의 REJECTED 로 적으면 나간 돈이 원장에서 사라진다. */
    @Test
    fun `답을 옮기지 못하면 거절이 아니라 알 수 없는 실패다`() {
        respondWith("""{"answers":[{"type":"ranking","name":"needs_review"}]}""")

        val ex = assertFailsWith<ExternalApiException> { provider.decide("gpt-6-luna", request) }

        assertEquals(FailureKind.UNKNOWN, ex.kind)
    }

    /** 실패 번역은 이미지 · 텍스트와 같은 표를 쓴다. */
    @Test
    fun `HTTP 실패는 다른 종류와 같은 표로 옮긴다`() {
        server.expect(requestTo("$BASE_URL${OpenAIDecisionClient.DECISIONS_PATH}"))
            .andRespond(
                withStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .body("""{"error":{"message":"천천히","code":"rate_limit_exceeded"}}""")
                    .contentType(MediaType.APPLICATION_JSON),
            )

        val ex = assertFailsWith<ExternalApiException> { provider.decide("gpt-6-luna", request) }

        assertEquals(FailureKind.THROTTLED, ex.kind)
    }

    @Test
    fun `모르는 모델은 보내기 전에 거절한다`() {
        val ex = assertFailsWith<ExternalApiException> { provider.decide("gpt-image-2", request) }

        assertEquals(FailureKind.REJECTED, ex.kind)
        // 요청이 나가지 않았다.
        server.verify()
    }

    private companion object {
        const val BASE_URL = "https://api.openai.test"
    }
}
