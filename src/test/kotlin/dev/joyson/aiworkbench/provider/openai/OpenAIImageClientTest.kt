package dev.joyson.aiworkbench.provider.openai

import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 전송 계층만 검증한다. 우리 도메인이 등장하지 않는 것이 이 테스트의 성질이다 —
 * 그래서 나중에 이 클라이언트를 별도 모듈로 옮겨도 테스트가 따라간다.
 */
class OpenAIImageClientTest {

    private val builder = RestClient.builder().baseUrl("https://api.openai.com")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = OpenAIImageClient(builder.build())

    private val request = OpenAIImageRequest(
        model = "gpt-image-2.5-flare",
        prompt = "고양이",
        n = 2,
        size = "1024x1024",
        quality = "high",
        background = "transparent",
        outputFormat = "png",
    )

    @Test
    fun `요청을 OpenAI 형식으로 보내고 base64 이미지를 받는다`() {
        server.expect(requestTo("https://api.openai.com${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.model").value("gpt-image-2.5-flare"))
            .andExpect(jsonPath("$.n").value(2))
            .andExpect(jsonPath("$.output_format").value("png"))
            .andRespond(
                withSuccess(
                    """{"created":1,"data":[{"b64_json":"AAAA","revised_prompt":"다듬은 문구"},{"b64_json":"BBBB"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = client.generate(OpenAIImageEndpoint.GENERATIONS.path, request)

        assertEquals(2, response.data.size)
        assertEquals("AAAA", response.data[0].b64Json)
        assertEquals("다듬은 문구", response.data[0].revisedPrompt)
        server.verify()
    }

    /** null 필드를 보내면 OpenAI 가 거부하거나 기본값을 덮어쓸 수 있다. */
    @Test
    fun `지정하지 않은 옵션은 요청에 실리지 않는다`() {
        server.expect(requestTo("https://api.openai.com${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"size\""))))
            .andRespond(withSuccess("""{"created":1,"data":[{"b64_json":"AAAA"}]}""", MediaType.APPLICATION_JSON))

        client.generate(OpenAIImageEndpoint.GENERATIONS.path, OpenAIImageRequest(model = "gpt-image-2.5-flare", prompt = "고양이"))

        server.verify()
    }

    @Test
    fun `에러 응답의 사유를 그대로 전달한다`() {
        server.expect(requestTo("https://api.openai.com${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":{"message":"Invalid size","type":"invalid_request_error","code":"bad_size"}}"""),
            )

        val ex = assertFailsWith<OpenAIException> { client.generate(OpenAIImageEndpoint.GENERATIONS.path, request) }

        assertEquals("Invalid size", ex.detail)
        assertEquals(false, ex.retryable, "4xx 는 다시 시도할 만하지 않다")
    }

    /** 재시도 판단은 호출자의 몫이지만, 판단 재료는 전송이 준다. */
    @Test
    fun `5xx 와 429 는 재시도 가능으로 표시한다`() {
        server.expect(requestTo("https://api.openai.com${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(withServerError())

        val ex = assertFailsWith<OpenAIException> { client.generate(OpenAIImageEndpoint.GENERATIONS.path, request) }

        assertTrue(ex.retryable)
    }

    @Test
    fun `본문을 파싱할 수 없는 실패도 상태 코드는 남긴다`() {
        server.expect(requestTo("https://api.openai.com${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>gateway</html>"))

        val ex = assertFailsWith<OpenAIException> { client.generate(OpenAIImageEndpoint.GENERATIONS.path, request) }

        assertEquals(HttpStatus.BAD_GATEWAY, ex.status)
    }
}
