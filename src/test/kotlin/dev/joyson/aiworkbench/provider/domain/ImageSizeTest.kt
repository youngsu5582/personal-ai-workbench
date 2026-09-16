package dev.joyson.aiworkbench.provider.domain

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 저장·전송 표기를 고정한다.
 *
 * 이 값들은 생성 옵션에 실려 DB 의 JSON 컬럼에 그대로 들어가고 API 응답에도 나간다.
 * 표기가 바뀌면 기존 행을 못 읽으므로, 바뀌면 테스트가 먼저 깨지게 둔다.
 *
 * 맨손으로 만든 매퍼가 아니라 **애플리케이션이 구성한 매퍼**로 확인한다.
 * 직접 만들면 등록된 모듈·설정이 빠져서, 테스트는 통과하는데 실제 저장·응답은 다를 수 있다.
 */
@ActiveProfiles("test")
@JsonTest
class ImageSizeTest @Autowired constructor(
    private val mapper: ObjectMapper,
) {

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
