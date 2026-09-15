package dev.joyson.aiworkbench.provider.domain

import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 저장·전송 표기를 고정한다.
 *
 * 이 값들은 생성 옵션에 실려 DB 의 JSON 컬럼에 그대로 들어간다.
 * 표기가 바뀌면 기존 행을 못 읽으므로, 바뀌면 테스트가 먼저 깨지게 둔다.
 */
class ImageSizeTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `비율로 말한 크기를 왕복시킨다`() {
        val size: ImageSize = ImageSize.ByRatio(AspectRatio.SIXTEEN_NINE, Resolution.TWO_K)

        val json = mapper.writeValueAsString(size)

        assertEquals("""{"type":"ratio","ratio":"16:9","resolution":"2k"}""", json)
        assertEquals(size, mapper.readValue<ImageSize>(json))
    }

    @Test
    fun `픽셀로 말한 크기를 왕복시킨다`() {
        val size: ImageSize = ImageSize.ByPixels(1920, 1080)

        val json = mapper.writeValueAsString(size)

        assertEquals("""{"type":"pixels","width":1920,"height":1080}""", json)
        assertEquals(size, mapper.readValue<ImageSize>(json))
    }

    @Test
    fun `품질도 상수 이름이 아니라 값으로 적힌다`() {
        assertEquals(""""xhigh"""", mapper.writeValueAsString(Quality.XHIGH))
        assertEquals(Quality.XHIGH, mapper.readValue<Quality>(""""xhigh""""))
    }
}
