package dev.joyson.aiworkbench.provider.gemini

import dev.joyson.aiworkbench.provider.ExternalApiAudioRequest
import dev.joyson.aiworkbench.provider.ExternalApiAudioResponse
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **실제 Gemini 음성 API 를 부른다.** 막는 방식은 [GeminiTextProviderLiveTest] 와 같다 — `GEMINI_LIVE=1` 과 프로젝트 전용 키.
 *
 * ```
 * GEMINI_LIVE=1 ./gradlew test --tests '*GeminiAudioProviderLiveTest*'
 * GEMINI_LIVE=1 GEMINI_TTS_MODEL=gemini-3.8-flash-lite-tts ./gradlew test --tests '*GeminiAudioProviderLiveTest.TTS*'
 * ```
 *
 * 문서로만 아는 것을 확인한다 — 단계 목록 · 주석 · `mime_type` 의 표기, 응답에서 소리가 있는 자리,
 * **`usage` 의 실제 생김새**(단가표가 가리키는 경로), 바이트로 읽은 길이가 출력 토큰(초당 25개)과 맞는지.
 *
 * 한도가 낮다(Free Tier 는 분당 3건). 한 번에 다 돌리면 세 번째가 429 일 수 있다.
 * 소리와 사용량은 `build/live-test/` 에 남는다 — 사람이 듣는다.
 */
@EnabledIf("liveEnabled")
class GeminiAudioProviderLiveTest {

    private val provider: GeminiAudioProvider by lazy {
        val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        val factory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(Duration.ofMinutes(3)) }
        GeminiAudioProvider(
            GeminiInteractionClient(
                RestClient.builder()
                    .baseUrl("https://generativelanguage.googleapis.com")
                    .defaultHeader(GeminiTextClient.API_KEY_HEADER, GeminiTextProviderLiveTest.apiKey()!!)
                    .requestFactory(factory)
                    .build(),
            ),
        )
    }

    private val model: String get() = System.getenv("GEMINI_TTS_MODEL") ?: "gemini-3.8-flash-tts"

    /** 한국어에 영어 기술 용어가 섞인 면접 질문 한 문장. */
    private val question = "그 쿠폰 발급 로직에서 동시성 문제는 어떻게 막으셨나요? Redis 분산 락과 비교해서 설명해 주세요."

    @Test
    fun `TTS 결과의 길이는 출력 토큰과 맞는다`() {
        val response = generate(ExternalApiAudioRequest(text = question, voice = "Kore"), "plain")

        assertEquals("audio/wav", response.mimeType)
        val outputTokens = (response.usage!!.raw["total_output_tokens"] as Number).toDouble()
        assertTrue(response.usage!!.raw["total_input_tokens"] is Number, "입력 토큰 경로가 단가표가 가리키는 자리에 없다")
        // 오디오 토큰은 초당 25개다. 토큰은 정수라 한 개(0.04초) 안쪽으로 맞으면 된다.
        assertTrue(
            abs(response.durationSeconds * TOKENS_PER_SECOND - outputTokens) <= 1.0,
            "길이 ${response.durationSeconds}초 × 25 와 출력 토큰 $outputTokens 이 어긋난다",
        )
    }

    @Test
    fun `말투를 주석으로 주면 받아들인다`() {
        val response = generate(ExternalApiAudioRequest(text = question, voice = "Kore", style = "차분하고 또렷하게"), "style")

        assertTrue(response.durationSeconds > 0)
    }

    private fun generate(request: ExternalApiAudioRequest, name: String): ExternalApiAudioResponse {
        val response = provider.generateAudio(model, request)
        val dir = Path.of("build/live-test")
        dir.resolve("live-gemini-tts-$name.wav").createParentDirectories().writeBytes(response.audio)
        dir.resolve("live-gemini-tts-$name.json").writeText(
            JsonMapper.shared().writerWithDefaultPrettyPrinter().writeValueAsString(
                mapOf(
                    "model" to model,
                    "id" to response.providerId,
                    "bytes" to response.audio.size,
                    "durationSeconds" to response.durationSeconds,
                    "usage" to response.usage?.raw,
                ),
            ),
        )
        println("[$name] ${response.audio.size}바이트 ${response.durationSeconds}초 usage=${response.usage?.raw}")
        return response
    }

    companion object {
        private const val TOKENS_PER_SECOND = 25

        @JvmStatic
        fun liveEnabled(): Boolean = GeminiTextProviderLiveTest.liveEnabled()
    }
}
