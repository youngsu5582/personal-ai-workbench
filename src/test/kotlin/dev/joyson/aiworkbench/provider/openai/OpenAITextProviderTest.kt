package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiTextRequest
import dev.joyson.aiworkbench.provider.FailureKind
import dev.joyson.aiworkbench.provider.ReasoningEffort
import dev.joyson.aiworkbench.provider.StopReason
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 번역만 검증한다 — 포트 어휘가 Responses API 의 어휘로 옮겨지는가, 응답의 여러 끝맺음이
 * [StopReason] 과 예외로 올바르게 갈리는가.
 *
 * 저쪽이 실제로 받아주는지는 [OpenAITextProviderLiveTest] 만 증명한다.
 */
class OpenAITextProviderTest {

    private val builder = RestClient.builder().baseUrl(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val provider = OpenAITextProvider(OpenAITextClient(builder.build()))

    private val usage = """{"input_tokens":12,"input_tokens_details":{"cached_tokens":0},""" +
        """"output_tokens":7,"output_tokens_details":{"reasoning_tokens":3},"total_tokens":19}"""

    /** 실제 응답처럼 보낸 파라미터의 메아리와 추론 항목을 섞는다. 글은 message 항목에만 있다. */
    private fun response(status: String, content: String, extra: String = "") =
        """{"id":"resp_1","object":"response","status":"$status","temperature":1.0,"tools":[],$extra
           "output":[{"type":"reasoning","id":"rs_1","summary":[]},
                     {"type":"message","id":"msg_1","role":"assistant","content":[$content]}],
           "usage":$usage}"""

    private fun outputText(text: String) = """{"type":"output_text","text":"$text","annotations":[]}"""

    private fun respondWith(body: String) {
        server.expect(requestTo("$BASE_URL${OpenAITextClient.RESPONSES_PATH}"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `텍스트 모델만 지원한다고 답한다`() {
        assertTrue(provider.supports("gpt-6-luna"))
        assertTrue(provider.supports("gpt-6-astra"))
        assertTrue(provider.supports("gpt-5.6-terra"))
        // 같은 OpenAI 라도 이미지 모델로는 글을 만들지 않는다.
        assertFalse(provider.supports("gpt-image-2"))
        assertFalse(provider.supports(""))
    }

    @Test
    fun `형식이 없으면 text 를 싣지 않고 저쪽에 남기지 않는다`() {
        server.expect(requestTo("$BASE_URL${OpenAITextClient.RESPONSES_PATH}"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.model").value("gpt-6-luna"))
            .andExpect(jsonPath("$.input").value("세 줄로 요약해"))
            .andExpect(jsonPath("$.instructions").value("너는 편집자다"))
            .andExpect(jsonPath("$.store").value(false))
            .andExpect(jsonPath("$.text").doesNotExist())
            // effort 를 주지 않으면 보내지 않는다 — 모델마다 기본값이 다르고, 그 기본값을 우리가 지어내지 않는다.
            .andExpect(jsonPath("$.reasoning").doesNotExist())
            .andRespond(withSuccess(response("completed", outputText("요약")), MediaType.APPLICATION_JSON))

        provider.generateText("gpt-6-luna", ExternalApiTextRequest("세 줄로 요약해", instructions = "너는 편집자다"))

        server.verify()
    }

    /**
     * 출력 키는 스키마의 키 순서대로 나온다. `reasoning` 을 먼저 두는 스키마는 그 순서가 곧 의도라,
     * 나가는 본문에서 순서가 바뀌면 답의 품질이 말없이 달라진다.
     */
    @Test
    fun `스키마를 받은 순서 그대로 strict 로 싣는다`() {
        val schema = """{"type":"object","properties":{"reasoning":{"type":"string"},"answer":{"type":"string"}},""" +
            """"required":["reasoning","answer"],"additionalProperties":false}"""
        server.expect(requestTo("$BASE_URL${OpenAITextClient.RESPONSES_PATH}"))
            .andExpect(jsonPath("$.text.format.type").value("json_schema"))
            .andExpect(jsonPath("$.text.format.name").value("output"))
            .andExpect(jsonPath("$.text.format.strict").value(true))
            .andExpect { request ->
                val body = (request as MockClientHttpRequest).bodyAsString
                assertTrue(body.contains(""""schema":$schema"""), "스키마가 원문 그대로 실리지 않았다: $body")
            }
            .andRespond(withSuccess(response("completed", outputText("""{}""")), MediaType.APPLICATION_JSON))

        provider.generateText("gpt-6-luna", ExternalApiTextRequest("질문", jsonSchema = schema))

        server.verify()
    }

    @Test
    fun `effort 는 reasoning 안에 싣는다`() {
        server.expect(requestTo("$BASE_URL${OpenAITextClient.RESPONSES_PATH}"))
            .andExpect(jsonPath("$.reasoning.effort").value("high"))
            .andRespond(withSuccess(response("completed", outputText("답")), MediaType.APPLICATION_JSON))

        provider.generateText("gpt-6-luna", ExternalApiTextRequest("질문", effort = ReasoningEffort.HIGH))

        server.verify()
    }

    @Test
    fun `다 된 응답에서 글과 사용량과 식별자를 꺼낸다`() {
        respondWith(response("completed", outputText("안녕")))

        val result = provider.generateText("gpt-6-luna", ExternalApiTextRequest("인사해"))

        assertEquals("안녕", result.text)
        assertEquals(StopReason.COMPLETED, result.stopReason)
        assertEquals("resp_1", result.providerId)
        // 원문 그대로다. 단가표가 이 경로를 가리킨다.
        assertEquals(12, result.usage!!.raw["input_tokens"])
        assertEquals(7, result.usage.raw["output_tokens"])
    }

    /** 거절도 과금된 응답이다. 예외로 바꾸면 사용량이 함께 버려진다. */
    @Test
    fun `거절은 예외가 아니라 사실로 답하고 사용량을 지킨다`() {
        respondWith(response("completed", """{"type":"refusal","refusal":"도울 수 없다"}"""))

        val result = provider.generateText("gpt-6-luna", ExternalApiTextRequest("질문"))

        assertEquals(StopReason.REFUSED, result.stopReason)
        assertEquals("도울 수 없다", result.text)
        assertEquals(19, result.usage!!.raw["total_tokens"])
    }

    @Test
    fun `토큰 한도에 걸린 응답은 잘린 글과 함께 답한다`() {
        respondWith(
            response("incomplete", outputText("""{\"ans"""), extra = """"incomplete_details":{"reason":"max_output_tokens"},"""),
        )

        val result = provider.generateText("gpt-6-luna", ExternalApiTextRequest("질문"))

        assertEquals(StopReason.MAX_TOKENS, result.stopReason)
        assertEquals("""{"ans""", result.text)
        assertEquals(19, result.usage!!.raw["total_tokens"])
    }

    @Test
    fun `콘텐츠 필터가 멈춘 응답을 가른다`() {
        respondWith(response("incomplete", "", extra = """"incomplete_details":{"reason":"content_filter"},"""))

        val result = provider.generateText("gpt-6-luna", ExternalApiTextRequest("질문"))

        assertEquals(StopReason.CONTENT_FILTER, result.stopReason)
        assertEquals("", result.text)
    }

    /** 문서화된 사유는 잘림과 필터 말고도 있다(`max_messages`). 그것도 과금된 응답이라 사용량을 지켜야 한다. */
    @Test
    fun `다른 이유로 멈춘 응답도 사용량과 함께 답한다`() {
        respondWith(response("incomplete", outputText("반쯤"), extra = """"incomplete_details":{"reason":"max_messages"},"""))

        val result = provider.generateText("gpt-6-luna", ExternalApiTextRequest("질문"))

        assertEquals(StopReason.INCOMPLETE, result.stopReason)
        assertEquals("반쯤", result.text)
        assertEquals("resp_1", result.providerId)
        assertEquals(19, result.usage!!.raw["total_tokens"])
    }

    @Test
    fun `저쪽 처리가 실패한 응답은 다시 해볼 만한 실패다`() {
        respondWith(response("failed", "", extra = """"error":{"code":"server_error","message":"터졌다"},"""))

        val ex = assertFailsWith<ExternalApiException> { provider.generateText("gpt-6-luna", ExternalApiTextRequest("질문")) }

        assertEquals(FailureKind.PROVIDER_ERROR, ex.kind)
        assertTrue(ex.message!!.contains("터졌다"), "저쪽 사유가 사라졌다: ${ex.message}")
    }

    @Test
    fun `다 됐다는데 글이 없으면 저쪽 사정으로 본다`() {
        respondWith(response("completed", ""))

        val ex = assertFailsWith<ExternalApiException> { provider.generateText("gpt-6-luna", ExternalApiTextRequest("질문")) }

        assertEquals(FailureKind.PROVIDER_ERROR, ex.kind)
    }

    @Test
    fun `모르는 상태는 다시 하지 않는다`() {
        respondWith(response("cancelled", outputText("안녕")))

        val ex = assertFailsWith<ExternalApiException> { provider.generateText("gpt-6-luna", ExternalApiTextRequest("질문")) }

        assertEquals(FailureKind.UNKNOWN, ex.kind)
        assertFalse(ex.retryable)
    }

    /** 실패 번역은 이미지 어댑터와 같은 표를 쓴다. 같은 429 가 작업 종류에 따라 다르게 적히면 안 된다. */
    @Test
    fun `HTTP 실패는 이미지와 같은 표로 옮긴다`() {
        server.expect(requestTo("$BASE_URL${OpenAITextClient.RESPONSES_PATH}"))
            .andRespond(
                withStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .body("""{"error":{"message":"천천히","code":"rate_limit_exceeded"}}""")
                    .contentType(MediaType.APPLICATION_JSON),
            )

        val ex = assertFailsWith<ExternalApiException> { provider.generateText("gpt-6-luna", ExternalApiTextRequest("질문")) }

        assertEquals(FailureKind.THROTTLED, ex.kind)
        assertTrue(ex.message!!.contains("429"), "상태 코드가 사라졌다: ${ex.message}")
    }

    @Test
    fun `모르는 모델은 보내기 전에 거절한다`() {
        val ex = assertFailsWith<ExternalApiException> { provider.generateText("gpt-image-2", ExternalApiTextRequest("질문")) }

        assertEquals(FailureKind.REJECTED, ex.kind)
        // 요청이 나가지 않았다.
        server.verify()
    }

    private companion object {
        const val BASE_URL = "https://api.openai.test"
    }
}
