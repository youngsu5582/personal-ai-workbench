package dev.joyson.aiworkbench.provider.openai

import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResult
import dev.joyson.aiworkbench.provider.ImageQuality
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * **실제 OpenAI 를 호출한다.** 키가 없으면 통째로 건너뛴다.
 *
 * 나머지 테스트는 전부 `MockRestServiceServer` 라 *"우리가 이렇게 보낸다"* 만 증명한다.
 * *"OpenAI 가 받아준다"* 는 이 테스트만 증명한다 — 엔드포인트 경로, 모델 이름,
 * 임의 크기 허용 여부, `quality` 값, 필드명(`output_format`·`b64_json`).
 *
 * `OpenAIImageClient` 만 따로 검증하지 않는 이유는 **비용이 같기 때문**이다.
 * 여기서 [ExternalApiGenerateRequest] 부터 넣으면 우리 번역 코드(크기 계산·품질 매핑·모델→경로·
 * base64 디코딩·예외 변환)까지 같은 호출 한 번으로 함께 밟힌다.
 *
 * ### 돌리는 법
 * ```
 * OPENAI_LIVE=1 WORKBENCH_PROVIDER_OPENAI_API_KEY=sk-... ./gradlew test --tests '*LiveTest*'
 * ```
 * 키는 `.env` 의 `WORKBENCH_PROVIDER_OPENAI_API_KEY` 에서도 읽는다.
 * 모델을 바꿔 확인하려면 `OPENAI_MODEL=gpt-image-2.5-flare` 를 함께 준다.
 *
 * ### 두 겹으로 막는다
 *
 * **1) `OPENAI_LIVE=1` 을 명시해야 돈다.** 키가 설정돼 있는 것은 앱을 띄우기 위한 정상 상태지
 * 돈을 써도 좋다는 뜻이 아니다. 키 유무만 조건으로 걸면 평범한 `./gradlew test` 한 번이
 * 과금 호출을 일으킨다.
 *
 * **2) 공용 이름 `OPENAI_API_KEY` 는 읽지 않는다.** 그 이름은 다른 CLI·에디터 플러그인·
 * 다른 프로젝트가 함께 쓰는 관례라, 이 테스트가 집어가면 엉뚱한 계정에 청구된다.
 * 이 프로젝트를 위해 일부러 설정한 이름만 읽는다.
 *
 * **호출마다 과금된다.** 1024² 약 2MB, 2048×1152 약 4MB 이미지가 장당 15~30초에 만들어지고,
 * 결과는 `build/live-test/` 에 남으니 눈으로 확인할 수 있다.
 */
@EnabledIf("liveEnabled")
class OpenAIProviderLiveTest {

    private val provider: OpenAIProvider by lazy {
        val httpClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build()
        val factory = JdkClientHttpRequestFactory(httpClient).apply {
            // 이 API 는 동기라 이미지를 다 만들 때까지 연결이 열려 있다.
            setReadTimeout(java.time.Duration.ofMinutes(3))
        }
        OpenAIProvider(
            OpenAIImageClient(
                RestClient.builder()
                    .baseUrl("https://api.openai.com")
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${apiKey()!!}")
                    .requestFactory(factory)
                    .build(),
            ),
        )
    }

    private val model: String get() = System.getenv("OPENAI_MODEL") ?: "gpt-image-2"

    @Test
    fun `정사각 이미지를 만든다`() {
        val result = generate(1024, 1024, "live-1024x1024.png")

        assertPng(result.image)
    }

    /**
     * 남아 있는 추측 중 제일 큰 것 — OpenAI 가 고정 목록만 받는지, 임의 `WIDTHxHEIGHT` 를 받는지.
     *
     * 400 이 나면 `ImageSize.ByPixels` 경로 전체와 `pixelsOf` 의 계산이 다시 설계된다
     * ("가장 가까운 허용 크기 고르기" 로). 예외 메시지에 허용 목록이 실려 오는지도 여기서 드러난다.
     */
    @Test
    fun `임의 픽셀 크기를 받아주는지 확인한다`() {
        // 고정 메뉴(1024x1024 · 1536x1024 · 1024x1536)에 **없는** 값이어야 의미가 있다.
        // 2048x1152 는 16:9 @ 2k 요청이 실제로 만드는 값이라, 이게 거절되면 흔한 요청 대부분이 깨진다.
        val result = runCatching { generate(2048, 1152, "live-2048x1152.png") }
            .onFailure { e ->
                if (e is ExternalApiException) {
                    fail("임의 크기가 거절됐다. 허용 목록을 여기서 확인하고 pixelsOf 를 고친다 → ${e.message}")
                }
            }
            .getOrThrow()

        assertPng(result.image)
    }

    private fun generate(width: Int, height: Int, fileName: String): ExternalApiGenerateResult {
        val request = ExternalApiGenerateRequest(
            prompt = "창밖을 보는 고양이, 수채화",
            width = width,
            height = height,
            quality = ImageQuality.LOW, // 확인이 목적이므로 제일 싼 쪽으로 부른다
        )

        val response = provider.generate(model, request)

        assertTrue(response.result.isNotEmpty(), "응답에 이미지가 없다")
        val result = response.result.first()

        val path = Path.of("build/live-test/$fileName")
        path.createParentDirectories()
        path.writeBytes(result.image)

        println(
            """
            |
            |  model          = $model
            |  요청 크기       = ${width}x$height
            |  응답 크기       = ${result.metadata.width}x${result.metadata.height}
            |  바이트         = ${result.metadata.fileSize}
            |  mimeType       = ${result.metadata.mimeType}
            |  revisedPrompt  = ${result.revisedPrompt ?: "(없음)"}
            |  저장           = ${path.toAbsolutePath()}
            |
            """.trimMargin(),
        )
        return result
    }

    /** 진짜 PNG 인지 매직 넘버로 본다. base64 디코딩이 어긋나면 여기서 걸린다. */
    private fun assertPng(bytes: ByteArray) {
        assertTrue(bytes.size > 1024, "이미지가 너무 작다: ${bytes.size} bytes")
        assertContentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            bytes.copyOfRange(0, 8),
            "PNG 매직 넘버가 아니다",
        )
    }

    companion object {
        /**
         * **이 프로젝트 전용 이름만** 읽는다. 공용 `OPENAI_API_KEY` 는 일부러 제외한다.
         *
         * `.env` 를 읽는 것은 프로젝트가 `spring.config.import: optional:file:.env[.properties]` 로
         * 같은 파일을 쓰기 때문이다 — 앱과 테스트가 같은 키를 보게 되어 두 군데 적지 않아도 된다.
         */
        @JvmStatic
        fun apiKey(): String? =
            System.getenv("WORKBENCH_PROVIDER_OPENAI_API_KEY")
                ?: dotEnv("WORKBENCH_PROVIDER_OPENAI_API_KEY")

        /** 키만으로는 부족하다 — 명시적 의사표시가 있어야 한다. */
        @JvmStatic
        fun liveEnabled(): Boolean =
            System.getenv("OPENAI_LIVE") == "1" && !apiKey().isNullOrBlank()

        private fun dotEnv(key: String): String? =
            Path.of(".env").toFile()
                .takeIf { it.isFile }
                ?.readLines()
                ?.firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("=")
                ?.trim()
                ?.trim('"')
    }
}
