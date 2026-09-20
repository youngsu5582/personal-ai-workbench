package dev.joyson.aiworkbench.generation.domain.option

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * 입력 참조의 저장 표기를 고정한다.
 *
 * [ImageSizeTest] 와 같은 이유로 **애플리케이션이 구성한 매퍼**를 쓴다 — 맨손 매퍼는
 * 등록된 모듈이 빠져서, 테스트는 통과하는데 실제 저장이 다를 수 있다.
 *
 * 여기 적힌 JSON 문자열이 곧 DB 계약이다. 바꾸려면 기존 행을 고쳐야 한다.
 */
@ActiveProfiles("test")
@JsonTest
class ImageSourceTest @Autowired constructor(
    private val mapper: ObjectMapper,
) {

    private val sourceUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")

    /** 구현체가 하나뿐이어도 판별자가 나간다 — 종류가 늘어도 기존 행을 그대로 읽으려는 것이다. */
    @Test
    fun `생성 결과 참조에는 종류가 함께 적힌다`() {
        val source: ImageSource = ImageSource.Generated(sourceUuid)

        val json = mapper.writeValueAsString(source)

        assertEquals("""{"type":"generated","uuid":"$sourceUuid"}""", json)
        assertEquals(source, mapper.readValue<ImageSource>(json))
    }

    @Test
    fun `이미지 변형 옵션을 왕복시킨다`() {
        val option: GenerationOption = ImageToImageOption(
            prompt = "수채화로",
            sources = listOf(ImageSource.Generated(sourceUuid)),
            size = ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K),
        )

        val json = mapper.writeValueAsString(option)

        assertEquals(
            """{"type":"image-to-image","prompt":"수채화로",""" +
                """"sources":[{"type":"generated","uuid":"$sourceUuid"}],""" +
                """"size":{"type":"ratio","ratio":"1:1","resolution":"1k"},"quality":"auto"}""",
            json,
        )

        val read = mapper.readValue<GenerationOption>(json)
        assertIs<ImageToImageOption>(read)
        assertIs<ImageSource.Generated>(read.sources.single())
        assertEquals(option, read)
    }

    /** 사용자 입력의 불변식은 도메인 타입이 지킨다 — 바인딩 단계에서 걸려야 400 이 된다. */
    @Test
    fun `입력 이미지가 없는 변형 옵션은 읽히지 않는다`() {
        val json = """{"type":"image-to-image","prompt":"수채화로","sources":[],""" +
            """"size":{"type":"ratio","ratio":"1:1","resolution":"1k"}}"""

        assertFailsWith<Exception> { mapper.readValue<GenerationOption>(json) }
    }
}
