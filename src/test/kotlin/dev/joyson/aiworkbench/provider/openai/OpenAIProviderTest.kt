package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ImageQuality
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 번역만 검증한다. HTTP 자체는 [OpenAIImageClientTest] 가 본다.
 *
 * 여기서 보는 것은 셋이다 — 모델이 올바른 경로로 가는가, 포트 어휘가 OpenAI 어휘로 옮겨지는가,
 * OpenAI 실패가 [ExternalApiException] 으로 바뀌는가.
 */
class OpenAIProviderTest {

    private val builder = RestClient.builder().baseUrl(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val provider = OpenAIProvider(OpenAIImageClient(builder.build()))

    private val image = "고양이".toByteArray()
    private val body = """{"created":1,"data":[{"b64_json":"${Base64.getEncoder().encodeToString(image)}"}]}"""

    private fun request(
        width: Int = 1024,
        height: Int = 1024,
        quality: ImageQuality = ImageQuality.HIGH,
    ) = ExternalApiGenerateRequest(prompt = "고양이", width = width, height = height, quality = quality)

    @Test
    fun `아는 모델만 지원한다고 답한다`() {
        assertTrue(provider.supports("gpt-image-2"))
        assertTrue(provider.supports("gpt-image-2.5-flare"))
        assertFalse(provider.supports("gemini-image"))
        // 사실 질의라 모르는 모델에도 예외를 던지지 않는다.
        assertFalse(provider.supports(""))
    }

    @Test
    fun `모델이 달라도 같은 경로로 보내고 모델은 본문에 싣는다`() {
        // 경로를 가르는 축은 모델 세대가 아니라 작업 종류다. 세대는 body 의 model 로 말한다.
        server.expect(requestTo("$BASE_URL${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andExpect(jsonPath("$.model").value("gpt-image-1"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        provider.generate("gpt-image-1", request())

        server.verify()
    }

    @Test
    fun `픽셀 크기를 해석하지 않고 그대로 보낸다`() {
        server.expect(requestTo("$BASE_URL${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andExpect(jsonPath("$.size").value("2048x1152"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val response = provider.generate("gpt-image-2", request(width = 2048, height = 1152))

        // 실제 쓰인 크기가 결과로 드러난다.
        assertEquals(2048, response.result[0].metadata.width)
        assertEquals(1152, response.result[0].metadata.height)
        server.verify()
    }

    @Test
    fun `포트 품질을 OpenAI 의 quality 로 옮긴다`() {
        server.expect(requestTo("$BASE_URL${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andExpect(jsonPath("$.quality").value("xhigh"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        provider.generate("gpt-image-2", request(quality = ImageQuality.XHIGH))

        server.verify()
    }

    @Test
    fun `base64 응답을 바이트로 풀어 돌려준다`() {
        server.expect(requestTo("$BASE_URL${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

        val response = provider.generate("gpt-image-2", request())

        assertEquals(1, response.result.size)
        assertContentEquals(image, response.result[0].image)
        assertEquals("image/png", response.result[0].metadata.mimeType)
        // 메타데이터의 파일 크기가 실제 바이트와 맞는다 — 어긋나면 생성자가 막는다.
        assertEquals(image.size, response.result[0].metadata.fileSize)
    }

    @Test
    fun `모르는 모델은 다시 보내도 소용없으므로 재시도 대상이 아니다`() {
        val ex = assertFailsWith<ExternalApiException> { provider.generate("gemini-image", request()) }

        assertFalse(ex.retryable)
    }

    @Test
    fun `429 는 재시도 대상이고 400 은 아니다`() {
        assertTrue(retryableFor(HttpStatus.TOO_MANY_REQUESTS))
        assertFalse(retryableFor(HttpStatus.BAD_REQUEST))
    }

    @Test
    fun `200 인데 이미지가 없으면 실패로 본다`() {
        server.expect(requestTo("$BASE_URL${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(withSuccess("""{"created":1,"data":[]}""", MediaType.APPLICATION_JSON))

        val ex = assertFailsWith<ExternalApiException> { provider.generate("gpt-image-2", request()) }

        assertTrue(ex.retryable)
    }

    /**
     * 한 테스트에서 호출을 여러 번 하려면 서버를 매번 새로 만들어야 한다 —
     * `MockRestServiceServer` 는 요청이 한 번 나간 뒤에는 기대를 더 등록하지 못한다.
     */
    private fun retryableFor(status: HttpStatus): Boolean {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo("$BASE_URL${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(
                withStatus(status).body("""{"error":{"message":"안된다"}}""")
                    .contentType(MediaType.APPLICATION_JSON),
            )

        return assertFailsWith<ExternalApiException> {
            OpenAIProvider(OpenAIImageClient(builder.build())).generate("gpt-image-2", request())
        }.retryable
    }

    private companion object {
        const val BASE_URL = "https://api.openai.com"
    }
}
