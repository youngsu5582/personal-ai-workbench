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
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.web.client.RestClient
import java.net.SocketTimeoutException
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
 * **모든** 실패가 [ExternalApiException] 으로 바뀌는가.
 *
 * 마지막 항목이 계약이다. 다른 종류의 예외가 하나라도 새면 부르는 쪽이 잡지 못해
 * Task 가 RUNNING 에 영원히 남고 그 호출의 지출도 기록되지 않는다.
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


    /**
     * 타임아웃은 상태 코드가 없어 [OpenAIException] 으로 걸리지 않는다.
     *
     * 저쪽은 이미 만들어 과금했는데 우리만 못 받은 경우가 여기 섞여 있어,
     * 이 경로가 새면 **가장 설명이 필요한 지출**이 기록에서 사라진다.
     */
    @Test
    fun `응답을 받지 못한 실패도 포트 예외로 바뀐다`() {
        server.expect(requestTo("$BASE_URL${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(withException(SocketTimeoutException("Read timed out")))

        val ex = assertFailsWith<ExternalApiException> { provider.generate("gpt-image-2", request()) }

        assertTrue(ex.retryable, "응답을 못 받은 것은 다시 해볼 만하다")
        assertTrue(ex.message!!.contains("응답 없음"), "원인이 메시지에서 사라졌다: ${ex.message}")
    }

    /**
     * 무엇이 샐지 미리 알 수 없으므로 종류로 막지 않고 전부 막는다.
     *
     * 여기서는 200 인데 본문이 JSON 이 아닌 경우로 찌른다 — 상태 코드도 정상이고 I/O 도 정상이라
     * 앞의 두 분기에 걸리지 않는다. 이 계약이 없으면 이런 응답 하나가 Task 를 영영 멈춘다.
     */
    @Test
    fun `설명되지 않는 예외도 경계를 넘지 못한다`() {
        server.expect(requestTo("$BASE_URL${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(withSuccess("이건 JSON 이 아니다", MediaType.APPLICATION_JSON))

        val ex = assertFailsWith<ExternalApiException> { provider.generate("gpt-image-2", request()) }

        // 원인을 모르는 채로 세 번 부르면 돈만 세 번 나간다.
        assertFalse(ex.retryable, "모르는 실패를 다시 시도하게 뒀다")
        assertTrue(ex.message!!.contains("알 수 없는 오류"), "메시지가 원인을 감췄다: ${ex.message}")
    }


    /**
     * **호출이 성공한 뒤**에 나는 실패다. 응답은 200 이고 돈은 이미 나갔는데 우리가 못 읽는 경우.
     *
     * 이 경로가 새면 부르는 쪽이 못 잡아 Task 가 RUNNING 에 영원히 남고, 그 호출의 지출도
     * 기록되지 않는다 — 지출을 빠짐없이 적으려는 이 기능의 목적이 정확히 여기서 깨진다.
     */
    @Test
    fun `깨진 base64 도 포트 예외로 바뀐다`() {
        server.expect(requestTo("$BASE_URL${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(withSuccess("""{"created":1,"data":[{"b64_json":"!!!이건 base64 가 아니다!!!"}]}""", MediaType.APPLICATION_JSON))

        val ex = assertFailsWith<ExternalApiException> { provider.generate("gpt-image-2", request()) }

        // 우리가 예상하고 던진 것이 아니라 포괄 catch 가 받은 것이다 — 원인을 모르니 재시도하지 않는다.
        assertFalse(ex.retryable, "모르는 실패를 다시 시도하게 뒀다")
        assertTrue(ex.message!!.contains("알 수 없는 오류"), "원인이 메시지에서 사라졌다: ${ex.message}")
    }


    /**
     * 우리가 **사실로 판단해** 던진 예외는 포괄 catch 에 삼켜지면 안 된다.
     *
     * try 가 메서드 전체를 덮으므로, 되던지지 않으면 여기서 정한 `retryable = true` 가
     * 포괄 catch 의 `false` 로 뒤집힌다. 그러면 다시 해볼 만한 실패를 한 번에 포기한다.
     */
    @Test
    fun `우리가 던진 판단은 재시도 여부가 뒤집히지 않는다`() {
        server.expect(requestTo("$BASE_URL${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(withSuccess("""{"created":1,"data":[]}""", MediaType.APPLICATION_JSON))

        val ex = assertFailsWith<ExternalApiException> { provider.generate("gpt-image-2", request()) }

        assertTrue(ex.retryable, "우리가 정한 재시도 여부가 덮였다")
        assertEquals("응답에 이미지가 없다", ex.message)
    }

    /** 바이트가 비면 `ImageMetadata` 의 불변식에 걸린다. 그것도 경계를 넘으면 안 된다. */
    @Test
    fun `빈 이미지도 포트 예외로 바뀐다`() {
        server.expect(requestTo("$BASE_URL${OpenAIImageEndpoint.GENERATIONS.path}"))
            .andRespond(withSuccess("""{"created":1,"data":[{"b64_json":""}]}""", MediaType.APPLICATION_JSON))

        assertFailsWith<ExternalApiException> { provider.generate("gpt-image-2", request()) }
    }

    private companion object {
        const val BASE_URL = "https://api.openai.com"
    }
}
