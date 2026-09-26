package dev.joyson.aiworkbench.provider.openai

import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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

    /**
     * 과금 근거라 파싱 단계에서 잃으면 되돌릴 방법이 없다 — 응답은 한 번뿐이다.
     * 항목을 하나씩 선언하지 않고 통째로 받는 이유도 같다: OpenAI 가 항목을 늘려도 따라 들어와야 한다.
     */
    @Test
    fun `사용량 블록을 통째로 받는다`() {
        server.expect(requestTo("https://api.openai.com${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(
                withSuccess(
                    """{"created":1,"data":[{"b64_json":"AAAA"}],
                       "usage":{"total_tokens":4200,"input_tokens":200,"output_tokens":4000,
                                "input_tokens_details":{"text_tokens":150,"image_tokens":50},
                                "우리가_모르는_항목":7}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val usage = client.generate(OpenAIImageEndpoint.GENERATIONS.path, request).usage

        assertEquals("4200", usage?.get("total_tokens").toString())
        assertEquals("7", usage?.get("우리가_모르는_항목").toString(), "선언하지 않은 항목이 사라졌다")
        val details = usage?.get("input_tokens_details") as Map<*, *>
        assertEquals("150", details["text_tokens"].toString())
        server.verify()
    }

    /** 사용량을 주지 않는 응답도 있다. 없다고 호출이 실패하면 안 된다. */
    @Test
    fun `사용량이 없는 응답도 읽는다`() {
        server.expect(requestTo("https://api.openai.com${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(withSuccess("""{"created":1,"data":[{"b64_json":"AAAA"}]}""", MediaType.APPLICATION_JSON))

        val response = client.generate(OpenAIImageEndpoint.GENERATIONS.path, request)

        assertNull(response.usage)
        assertEquals(1, response.data.size)
    }

    /**
     * `unknown` 에 **무엇이 들어가고 무엇이 안 들어가는지**를 못박는다.
     *
     * 선언된 필드(`created`·`data`·`usage`)는 자기 자리로 가고, 나머지만 여기 남는다.
     * 이미지 바이트가 `data` 안이라 **구조적으로 섞일 수 없다**는 것이 이 방식의 요점이다 —
     * 원문을 통째로 로그에 찍었다면 base64 수 MB 가 딸려 나왔을 것이다.
     */
    @Test
    fun `선언하지 않은 최상위 필드만 따로 받는다`() {
        server.expect(requestTo("https://api.openai.com${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(
                withSuccess(
                    """{"created":1,"data":[{"b64_json":"AAAA"}],
                       "usage":{"total_tokens":210},
                       "size":"1024x1024","quality":"high","앞으로_생길_필드":{"a":1}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = client.generate(OpenAIImageEndpoint.GENERATIONS.path, request)

        // 선언하지 않은 것만 들어온다.
        assertEquals(setOf("size", "quality", "앞으로_생길_필드"), response.unknown.names)
        assertEquals("1024x1024", response.unknown.asMap()["size"])

        // 선언한 것은 자기 자리로 간다 — unknown 에 중복으로 담기지 않는다.
        assertEquals(1L, response.created)
        assertEquals("AAAA", response.data.single().b64Json)
        assertEquals("210", response.usage?.get("total_tokens").toString())
        assertFalse(response.unknown.names.contains("data"), "이미지가 unknown 으로 샜다")
        assertFalse(response.unknown.names.contains("usage"))
    }

    /** 모르는 필드가 없으면 비어 있다. 있을 때만 경고할 수 있어야 한다. */
    @Test
    fun `모르는 필드가 없으면 비어 있다`() {
        server.expect(requestTo("https://api.openai.com${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(withSuccess("""{"created":1,"data":[{"b64_json":"AAAA"}]}""", MediaType.APPLICATION_JSON))

        assertTrue(client.generate(OpenAIImageEndpoint.GENERATIONS.path, request).unknown.isEmpty)
    }

    /**
     * 최상위 갈고리는 결과물 **안**을 못 본다 — 표가 타입마다 따로이기 때문이다.
     * 여기 갈고리가 없으면 `data[]` 원소의 미지 필드는 온 줄도 모르고 사라진다.
     */
    @Test
    fun `결과물 안의 모르는 필드도 따로 받는다`() {
        server.expect(requestTo("https://api.openai.com${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(
                withSuccess(
                    """{"created":1,"data":[{"b64_json":"AAAA","revised_prompt":"다듬은 문구",
                       "앞으로_생길_필드":{"a":1}}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val image = client.generate(OpenAIImageEndpoint.GENERATIONS.path, request).data.single()

        assertEquals(setOf("앞으로_생길_필드"), image.unknown.names)
        // 선언한 것은 자기 자리로 간다 — 이미지가 unknown 으로 샐 수 없다.
        assertEquals("AAAA", image.b64Json)
        assertEquals("다듬은 문구", image.revisedPrompt)
        assertFalse(image.unknown.names.contains("b64_json"), "이미지가 unknown 으로 샜다")
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
        assertEquals(HttpStatus.BAD_REQUEST, ex.status, "상태 코드가 해석되지 않고 그대로 올라와야 한다")
    }

    @Test
    fun `본문을 파싱할 수 없는 실패도 상태 코드는 남긴다`() {
        server.expect(requestTo("https://api.openai.com${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>gateway</html>"))

        val ex = assertFailsWith<OpenAIException> { client.generate(OpenAIImageEndpoint.GENERATIONS.path, request) }

        assertEquals(HttpStatus.BAD_GATEWAY, ex.status)
    }
}
