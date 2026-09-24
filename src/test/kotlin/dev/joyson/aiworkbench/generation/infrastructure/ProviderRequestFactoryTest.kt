package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.Quality
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.provider.ImageQuality
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderRequestFactoryTest {

    private val factory = ProviderRequestFactory

    private fun sizeOf(ratio: AspectRatio, resolution: Resolution): String {
        val request = factory.from(TextToImageOption("고양이", ImageSize.ByRatio(ratio, resolution)))
        return "${request.width}x${request.height}"
    }

    @Test
    fun `비율과 해상도를 픽셀로 확정한다`() {
        // 긴 변이 해상도가 되고, 짧은 변은 같은 배율로 따라간다.
        assertEquals("1024x1024", sizeOf(AspectRatio.ONE_ONE, Resolution.ONE_K))
        assertEquals("2048x1152", sizeOf(AspectRatio.SIXTEEN_NINE, Resolution.TWO_K))
        assertEquals("576x1024", sizeOf(AspectRatio.NINE_SIXTEEN, Resolution.ONE_K))
        assertEquals("768x1024", sizeOf(AspectRatio.THREE_FOUR, Resolution.ONE_K))
        // 나누어떨어지지 않으면 내림한다. 4096 * 2 / 3 = 2730.67
        assertEquals("4096x2730", sizeOf(AspectRatio.THREE_TWO, Resolution.FOUR_K))
    }

    @Test
    fun `픽셀로 말한 크기는 해석하지 않는다`() {
        val request = factory.from(TextToImageOption("고양이", ImageSize.ByPixels(1920, 1080)))

        assertEquals(1920, request.width)
        assertEquals(1080, request.height)
    }

    @Test
    fun `제품 품질 눈금을 포트 눈금으로 옮긴다`() {
        // 지금은 1:1 이다. 한쪽에 값이 늘면 when 이 컴파일 에러로 알려준다.
        Quality.entries.forEach { quality ->
            val request = factory.from(
                TextToImageOption("고양이", ImageSize.ByPixels(1024, 1024), quality),
            )
            assertEquals(ImageQuality.valueOf(quality.name), request.quality)
        }
    }

    @Test
    fun `프롬프트를 그대로 넘긴다`() {
        val request = factory.from(TextToImageOption("창밖을 보는 고양이", ImageSize.ByPixels(512, 512)))

        assertEquals("창밖을 보는 고양이", request.prompt)
    }
}
