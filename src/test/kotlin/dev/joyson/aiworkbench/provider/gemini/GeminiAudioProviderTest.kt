package dev.joyson.aiworkbench.provider.gemini

import dev.joyson.aiworkbench.provider.ExternalApiAudioRequest
import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.FailureKind
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 번역만 검증한다 — 포트 어휘가 Interactions 의 어휘로 옮겨지는가, 응답에서 소리 · 길이 · 사용량을 꺼내는가,
 * 쓸 수 없는 응답이 알맞은 실패로 갈리는가.
 *
 * 저쪽이 실제로 받아주는지는 [GeminiAudioProviderLiveTest] 만 증명한다.
 */
class GeminiAudioProviderTest {

    private val builder = RestClient.builder().baseUrl(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val provider = GeminiAudioProvider(GeminiInteractionClient(builder.build()))

    /** 24kHz 모노 16bit 1초에, 실제 응답처럼 뒤에 출처 청크가 붙는다. */
    private val wav: ByteArray = run {
        val data = 48_000
        val tail = 10
        ByteBuffer.allocate(44 + data + 8 + tail).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + data + 8 + tail).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(24_000).putInt(48_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(data).put(ByteArray(data))
            .put("C2PA".toByteArray()).putInt(tail).put(ByteArray(tail))
            .array()
    }

    private val usage = """{"total_tokens":57,"total_input_tokens":32,"input_tokens_by_modality":[{"modality":"text","tokens":32}],""" +
        """"total_output_tokens":25,"output_tokens_by_modality":[{"modality":"audio","tokens":25}],"total_thought_tokens":0}"""

    /** 실제 응답처럼 입력 단계가 함께 돌아오고, 소리는 `model_output` 단계에 있다. */
    private fun response(status: String = "completed", audio: String? = Base64.getEncoder().encodeToString(wav)) =
        """{"id":"v1_abc","status":"$status","usage":$usage,"service_tier":"standard","steps":[""" +
            """{"type":"user_input","content":[{"type":"text","text":"질문"}]}""" +
            (audio?.let { """,{"type":"model_output","content":[{"type":"audio","data":"$it"}]}""" } ?: "") +
            "]}"

    @Test
    fun `음성 모델만 지원하고 목소리 30개를 밝힌다`() {
        assertTrue(provider.supports("gemini-3.8-flash-tts"))
        assertTrue(provider.supports("gemini-3.8-flash-lite-tts"))
        assertFalse(provider.supports("gemini-3.6-flash"))

        assertEquals(30, provider.voices(MODEL).size)
        assertTrue("Kore" in provider.voices(MODEL))
        assertEquals(emptySet(), provider.voices("gemini-3.6-flash"))
    }

    @Test
    fun `글과 목소리를 단계 목록으로 보내고 WAV 를 요청한다`() {
        server.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.model").value(MODEL))
            .andExpect(jsonPath("$.input[0].type").value("user_input"))
            .andExpect(jsonPath("$.input[0].content[0].type").value("text"))
            .andExpect(jsonPath("$.input[0].content[0].text").value("동시성 문제는 어떻게 막으셨나요?"))
            // 말투가 없으면 주석 칸째 보내지 않는다.
            .andExpect(jsonPath("$.input[0].content[0].annotations").doesNotExist())
            .andExpect(jsonPath("$.response_format.type").value("audio"))
            .andExpect(jsonPath("$.response_format.mime_type").value("audio/wav"))
            .andExpect(jsonPath("$.generation_config.speech_config[0].voice").value("Kore"))
            .andRespond(withSuccess(response(), MediaType.APPLICATION_JSON))

        provider.generateAudio(MODEL, ExternalApiAudioRequest(text = "동시성 문제는 어떻게 막으셨나요?", voice = "Kore"))

        server.verify()
    }

    /** 글 속에 쓰면 그 지시까지 소리 내어 읽힌다. 그래서 글과 따로 주석으로 싣는다. */
    @Test
    fun `말투는 글이 아니라 주석으로 싣는다`() {
        server.expect(requestTo(URL))
            .andExpect(jsonPath("$.input[0].content[0].text").value("질문"))
            .andExpect(jsonPath("$.input[0].content[0].annotations[0].type").value("speech_metadata"))
            .andExpect(jsonPath("$.input[0].content[0].annotations[0].style").value("차분하고 또렷하게"))
            .andRespond(withSuccess(response(), MediaType.APPLICATION_JSON))

        provider.generateAudio(MODEL, ExternalApiAudioRequest(text = "질문", voice = "Kore", style = "차분하고 또렷하게"))

        server.verify()
    }

    @Test
    fun `소리는 받은 그대로 두고 길이는 data 청크로 읽고 사용량은 원문 그대로 싣는다`() {
        server.expect(requestTo(URL)).andRespond(withSuccess(response(), MediaType.APPLICATION_JSON))

        val result = provider.generateAudio(MODEL, ExternalApiAudioRequest(text = "질문", voice = "Kore"))

        assertContentEquals(wav, result.audio, "뒤에 붙은 청크까지 그대로여야 한다")
        assertEquals("audio/wav", result.mimeType)
        assertEquals(1.0, result.durationSeconds, 1e-9)
        assertEquals(32, result.usage!!.raw["total_input_tokens"])
        assertEquals(25, result.usage!!.raw["total_output_tokens"])
        assertEquals("v1_abc", result.providerId)
    }

    @Test
    fun `끝나지 않은 응답은 다시 보내지 않는 실패다`() {
        server.expect(requestTo(URL)).andRespond(withSuccess(response(status = "in_progress"), MediaType.APPLICATION_JSON))

        val ex = assertFailsWith<ExternalApiException> { provider.generateAudio(MODEL, ExternalApiAudioRequest("질문", "Kore")) }

        assertEquals(FailureKind.UNKNOWN, ex.kind)
    }

    @Test
    fun `끝났는데 소리가 없으면 저쪽 사정으로 본다`() {
        server.expect(requestTo(URL)).andRespond(withSuccess(response(audio = null), MediaType.APPLICATION_JSON))

        val ex = assertFailsWith<ExternalApiException> { provider.generateAudio(MODEL, ExternalApiAudioRequest("질문", "Kore")) }

        assertEquals(FailureKind.PROVIDER_ERROR, ex.kind)
    }

    /** 길이 없이 저장하지 않는다. 다시 받아도 같은 형식일 것이라 돈만 다시 나간다. */
    @Test
    fun `길이를 읽을 수 없는 소리는 형식을 밝히고 다시 보내지 않는 실패다`() {
        val mp3 = Base64.getEncoder().encodeToString("ID3".toByteArray() + ByteArray(100))
        server.expect(requestTo(URL)).andRespond(withSuccess(response(audio = mp3), MediaType.APPLICATION_JSON))

        val ex = assertFailsWith<ExternalApiException> { provider.generateAudio(MODEL, ExternalApiAudioRequest("질문", "Kore")) }

        assertEquals(FailureKind.UNKNOWN, ex.kind)
        assertTrue(ex.message!!.contains("494433"), "받은 형식의 앞 바이트가 사유에 있어야 한다: ${ex.message}")
    }

    /** Interactions 의 오류 본문은 `code` 가 숫자가 아니라 오류 이름이다. 실제 429 본문의 모양이다. */
    @Test
    fun `HTTP 실패를 상태 코드의 의미로 옮기고 오류 이름을 남긴다`() {
        val cases = listOf(
            Triple(HttpStatus.TOO_MANY_REQUESTS, "too_many_requests", FailureKind.THROTTLED),
            Triple(HttpStatus.SERVICE_UNAVAILABLE, "unavailable", FailureKind.PROVIDER_ERROR),
            Triple(HttpStatus.BAD_REQUEST, "invalid_request", FailureKind.REJECTED),
        )
        for ((status, code, kind) in cases) {
            server.reset()
            server.expect(requestTo(URL)).andRespond(
                withStatus(status)
                    .body("""{"error":{"message":"Rate limit exceeded for model $MODEL","code":"$code"}}""")
                    .contentType(MediaType.APPLICATION_JSON),
            )

            val ex = assertFailsWith<ExternalApiException> { provider.generateAudio(MODEL, ExternalApiAudioRequest("질문", "Kore")) }

            assertEquals(kind, ex.kind, status.toString())
            assertTrue(ex.message!!.contains(code), "저쪽 오류 이름이 사라졌다: ${ex.message}")
            assertTrue(ex.message!!.contains("Rate limit exceeded"), "저쪽 설명이 사라졌다: ${ex.message}")
        }
    }

    @Test
    fun `모르는 모델과 모르는 목소리는 보내기 전에 거절한다`() {
        val unknownModel = assertFailsWith<ExternalApiException> { provider.generateAudio("gemini-3.6-flash", ExternalApiAudioRequest("질문", "Kore")) }
        val unknownVoice = assertFailsWith<ExternalApiException> { provider.generateAudio(MODEL, ExternalApiAudioRequest("질문", "kore")) }

        assertEquals(FailureKind.REJECTED, unknownModel.kind)
        assertEquals(FailureKind.REJECTED, unknownVoice.kind)
        server.verify()
    }

    private companion object {
        const val BASE_URL = "https://gemini.test"
        const val MODEL = "gemini-3.8-flash-tts"
        const val URL = "$BASE_URL/v1beta/interactions"
    }
}
