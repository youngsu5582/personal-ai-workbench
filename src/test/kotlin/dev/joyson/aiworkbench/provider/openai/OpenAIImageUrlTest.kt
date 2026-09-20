package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiFileInput
import dev.joyson.aiworkbench.provider.FailureKind
import dev.joyson.aiworkbench.provider.ImageQuality
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.URI
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 실제 HTTP 직렬화까지 거쳐 URL이 edits의 JSON 계약으로 전달되는지 확인한다. */
class OpenAIImageUrlTest {
    private val builder = RestClient.builder().baseUrl("https://api.openai.com")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val provider = OpenAIProvider(OpenAIImageClient(builder.build()))
    private val url = URI.create("https://storage.test/source.png?X-Amz-Signature=secret")
    private val bytes = byteArrayOf(1, 2, 3)
    private val response = """{"data":[{"b64_json":"${Base64.getEncoder().encodeToString(bytes)}"}]}"""

    private fun request(images: List<ExternalApiFileInput>) = ExternalApiGenerateRequest(
        prompt = "수채화로", width = 1024, height = 1024, quality = ImageQuality.HIGH, images = images,
    )

    @Test
    fun `presigned URL은 JSON images image_url로 전달한다`() {
        server.expect(requestTo("https://api.openai.com/v1/images/edits"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.images[0].image_url").value(url.toString()))
            .andExpect(jsonPath("$.model").value("gpt-image-2"))
            .andExpect(jsonPath("$.prompt").value("수채화로"))
            .andExpect(jsonPath("$.n").value(1))
            .andExpect(jsonPath("$.size").value("1024x1024"))
            .andExpect(jsonPath("$.quality").value("high"))
            .andExpect(jsonPath("$.output_format").value("png"))
            .andExpect(jsonPath("$.images[0].bytes").doesNotExist())
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON))

        val result = provider.generate("gpt-image-2", request(listOf(ExternalApiFileInput.Url(url, "image/png", "source.png"))))

        assertContentEquals(bytes, result.result.single().image)
        server.verify()
    }

    @Test
    fun `주소와 바이트가 섞여도 입력 순서를 유지하고 바이트는 data URL로 보낸다`() {
        server.expect(requestTo("https://api.openai.com/v1/images/edits"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.images[0].image_url").value("data:image/png;base64,AQID"))
            .andExpect(jsonPath("$.images[1].image_url").value(url.toString()))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON))

        provider.generate("gpt-image-2", request(listOf(
            ExternalApiFileInput.Bytes(bytes, "image/png", "source.png"),
            ExternalApiFileInput.Url(url, "image/png", "source.png"),
        )))

        server.verify()
    }

    @Test
    fun `URL 편집 실패도 기존 Provider 실패 규약으로 전달한다`() {
        server.expect(requestTo("https://api.openai.com/v1/images/edits"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("""{"error":{"message":"Invalid image"}}"""))

        val error = assertFailsWith<ExternalApiException> {
            provider.generate("gpt-image-2", request(listOf(ExternalApiFileInput.Url(url, "image/png", "source.png"))))
        }

        assertEquals(FailureKind.REJECTED, error.kind)
        server.verify()
    }
}
