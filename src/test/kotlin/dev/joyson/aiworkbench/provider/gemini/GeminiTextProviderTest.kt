package dev.joyson.aiworkbench.provider.gemini

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
 * 번역만 검증한다 — 포트 어휘가 `generateContent` 의 어휘로 옮겨지는가, 응답의 여러 끝맺음이
 * [StopReason] 과 예외로 올바르게 갈리는가.
 *
 * 저쪽이 실제로 받아주는지는 [GeminiTextProviderLiveTest] 만 증명한다.
 */
class GeminiTextProviderTest {

    private val builder = RestClient.builder().baseUrl(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val provider = GeminiTextProvider(GeminiTextClient(builder.build()))

    private val usage = """{"promptTokenCount":12,"candidatesTokenCount":7,"thoughtsTokenCount":21,"totalTokenCount":40,""" +
        """"promptTokensDetails":[{"modality":"TEXT","tokenCount":12}]}"""

    /** 실제 응답처럼 생각 요약 · 생각 서명 · 안전 평가를 섞는다. 답은 thought 가 아닌 파트에만 있다. */
    private fun response(finishReason: String, parts: String, extra: String = "") =
        """{"candidates":[{"content":{"parts":[$parts],"role":"model"},"finishReason":"$finishReason","index":0,"safetyRatings":[]}],$extra
           "usageMetadata":$usage,"modelVersion":"$MODEL","responseId":"resp-1"}"""

    private fun text(text: String) = """{"text":"$text","thoughtSignature":"c2ln"}"""

    private val thought = """{"text":"먼저 인사말을 고른다","thought":true}"""

    private fun respondWith(body: String) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `텍스트 모델만 지원한다고 답한다`() {
        assertTrue(provider.supports("gemini-3.6-flash"))
        assertTrue(provider.supports("gemini-3.5-flash-lite"))
        assertFalse(provider.supports("gpt-6-luna"))
        assertFalse(provider.supports(""))
    }

    @Test
    fun `thinkingLevel 이 표현하는 넷만 받는다고 밝힌다`() {
        assertEquals(
            setOf(ReasoningEffort.MINIMAL, ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            provider.efforts,
        )
    }

    @Test
    fun `모델은 경로로, 글과 역할은 contents 와 systemInstruction 으로 싣는다`() {
        server.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.contents[0].role").value("user"))
            .andExpect(jsonPath("$.contents[0].parts[0].text").value("세 줄로 요약해"))
            .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("너는 편집자다"))
            .andExpect(jsonPath("$.systemInstruction.role").doesNotExist())
            // 형식도 effort 도 주지 않으면 칸째 보내지 않는다 — 모델 기본값을 지어내지 않는다.
            .andExpect(jsonPath("$.generationConfig").doesNotExist())
            .andRespond(withSuccess(response("STOP", text("요약")), MediaType.APPLICATION_JSON))

        provider.generateText(MODEL, ExternalApiTextRequest("세 줄로 요약해", instructions = "너는 편집자다"))

        server.verify()
    }

    /** 출력 키는 스키마의 키 순서대로 나온다. 나가는 본문에서 순서가 바뀌면 답의 품질이 말없이 달라진다. */
    @Test
    fun `스키마를 받은 순서 그대로 responseJsonSchema 에 싣는다`() {
        val schema = """{"type":"object","properties":{"reasoning":{"type":"string"},"answer":{"type":"string"}},""" +
            """"required":["reasoning","answer"]}"""
        server.expect(requestTo(URL))
            .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
            .andExpect(jsonPath("$.generationConfig.thinkingConfig").doesNotExist())
            .andExpect { request ->
                val body = (request as MockClientHttpRequest).bodyAsString
                assertTrue(body.contains(""""responseJsonSchema":$schema"""), "스키마가 원문 그대로 실리지 않았다: $body")
            }
            .andRespond(withSuccess(response("STOP", text("{}")), MediaType.APPLICATION_JSON))

        provider.generateText(MODEL, ExternalApiTextRequest("질문", jsonSchema = schema))

        server.verify()
    }

    @Test
    fun `effort 는 thinkingLevel 로 싣는다`() {
        server.expect(requestTo(URL))
            .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("MINIMAL"))
            .andExpect(jsonPath("$.generationConfig.responseMimeType").doesNotExist())
            .andRespond(withSuccess(response("STOP", text("답")), MediaType.APPLICATION_JSON))

        provider.generateText(MODEL, ExternalApiTextRequest("질문", effort = ReasoningEffort.MINIMAL))

        server.verify()
    }

    /** 접수가 거르지만 포트를 바로 부르는 길도 있다. 보낼 말이 없으니 부르지 않는다. */
    @Test
    fun `표현할 수 없는 effort 는 보내기 전에 거절한다`() {
        for (effort in listOf(ReasoningEffort.NONE, ReasoningEffort.XHIGH, ReasoningEffort.MAX)) {
            val ex = assertFailsWith<ExternalApiException> {
                provider.generateText(MODEL, ExternalApiTextRequest("질문", effort = effort))
            }
            assertEquals(FailureKind.REJECTED, ex.kind, effort.name)
        }
        server.verify()
    }

    @Test
    fun `생각의 요약은 빼고 답과 사용량과 식별자를 꺼낸다`() {
        respondWith(response("STOP", "$thought,${text("안녕")}"))

        val result = provider.generateText(MODEL, ExternalApiTextRequest("인사해"))

        assertEquals("안녕", result.text)
        assertEquals(StopReason.COMPLETED, result.stopReason)
        assertEquals("resp-1", result.providerId)
        // 원문 그대로다. 단가표가 이 경로를 가리킨다.
        assertEquals(12, result.usage!!.raw["promptTokenCount"])
        assertEquals(21, result.usage.raw["thoughtsTokenCount"])
    }

    /** 막힌 프롬프트에는 후보가 없다. 예외로 바꾸면 그 호출의 사용량이 사라진다. */
    @Test
    fun `프롬프트가 막히면 필터로 답하고 사용량을 지킨다`() {
        respondWith("""{"promptFeedback":{"blockReason":"SAFETY","safetyRatings":[]},"usageMetadata":{"promptTokenCount":9,"totalTokenCount":9},"responseId":"resp-2"}""")

        val result = provider.generateText(MODEL, ExternalApiTextRequest("질문"))

        assertEquals(StopReason.CONTENT_FILTER, result.stopReason)
        assertEquals("", result.text)
        assertEquals(9, result.usage!!.raw["promptTokenCount"])
        assertEquals("resp-2", result.providerId)
    }

    @Test
    fun `토큰 한도에 걸린 응답은 잘린 글과 함께 답한다`() {
        respondWith(response("MAX_TOKENS", text("""{\"ans""")))

        val result = provider.generateText(MODEL, ExternalApiTextRequest("질문"))

        assertEquals(StopReason.MAX_TOKENS, result.stopReason)
        assertEquals("""{"ans""", result.text)
        assertEquals(40, result.usage!!.raw["totalTokenCount"])
    }

    @Test
    fun `내용 때문에 멈춘 응답은 필터로 가른다`() {
        for (reason in listOf("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII")) {
            server.reset()
            respondWith(response(reason, ""))

            assertEquals(StopReason.CONTENT_FILTER, provider.generateText(MODEL, ExternalApiTextRequest("질문")).stopReason, reason)
        }
    }

    /** 이유는 Google 이 늘려 간다. 모르는 이유도 과금된 응답이라 사용량을 지켜야 한다. */
    @Test
    fun `다른 이유로 멈춘 응답도 사용량과 함께 답한다`() {
        respondWith(response("LANGUAGE", text("반쯤")))

        val result = provider.generateText(MODEL, ExternalApiTextRequest("질문"))

        assertEquals(StopReason.INCOMPLETE, result.stopReason)
        assertEquals("반쯤", result.text)
        assertEquals("resp-1", result.providerId)
        assertEquals(40, result.usage!!.raw["totalTokenCount"])
    }

    @Test
    fun `다 됐다는데 답이 없으면 저쪽 사정으로 본다`() {
        // 생각만 하고 답을 내지 않은 경우다.
        respondWith(response("STOP", thought))

        val ex = assertFailsWith<ExternalApiException> { provider.generateText(MODEL, ExternalApiTextRequest("질문")) }

        assertEquals(FailureKind.PROVIDER_ERROR, ex.kind)
    }

    @Test
    fun `막히지도 않았는데 후보가 없으면 저쪽 사정으로 본다`() {
        respondWith("""{"usageMetadata":$usage,"responseId":"resp-3"}""")

        val ex = assertFailsWith<ExternalApiException> { provider.generateText(MODEL, ExternalApiTextRequest("질문")) }

        assertEquals(FailureKind.PROVIDER_ERROR, ex.kind)
    }

    @Test
    fun `HTTP 실패를 상태 코드의 의미로 옮기고 저쪽 오류 이름을 남긴다`() {
        val cases = listOf(
            HttpStatus.TOO_MANY_REQUESTS to FailureKind.THROTTLED,
            HttpStatus.SERVICE_UNAVAILABLE to FailureKind.PROVIDER_ERROR,
            HttpStatus.BAD_REQUEST to FailureKind.REJECTED,
        )
        for ((status, kind) in cases) {
            server.reset()
            server.expect(requestTo(URL)).andRespond(
                withStatus(status)
                    .body("""{"error":{"code":${status.value()},"message":"사유","status":"REASON_${status.value()}"}}""")
                    .contentType(MediaType.APPLICATION_JSON),
            )

            val ex = assertFailsWith<ExternalApiException> { provider.generateText(MODEL, ExternalApiTextRequest("질문")) }

            assertEquals(kind, ex.kind, status.toString())
            assertTrue(ex.message!!.contains("${status.value()}"), "상태 코드가 사라졌다: ${ex.message}")
            assertTrue(ex.message!!.contains("REASON_${status.value()}"), "저쪽 오류 이름이 사라졌다: ${ex.message}")
        }
    }

    @Test
    fun `모르는 모델은 보내기 전에 거절한다`() {
        val ex = assertFailsWith<ExternalApiException> { provider.generateText("gemini-3.1-flash-image", ExternalApiTextRequest("질문")) }

        assertEquals(FailureKind.REJECTED, ex.kind)
        server.verify()
    }

    private companion object {
        const val BASE_URL = "https://gemini.test"
        const val MODEL = "gemini-3.6-flash"
        const val URL = "$BASE_URL/v1beta/models/$MODEL:generateContent"
    }
}
