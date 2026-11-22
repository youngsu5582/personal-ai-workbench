package dev.joyson.aiworkbench.generation.domain.option

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TTS 옵션의 저장 표기를 고정한다. [TextGenerateOptionTest] 와 같은 이유로 애플리케이션이 구성한 매퍼를 쓴다.
 *
 * 여기 적힌 JSON 문자열이 곧 DB 계약이다. 바꾸려면 기존 행을 고쳐야 한다.
 */
@ActiveProfiles("test")
@JsonTest
class AudioGenerateOptionTest @Autowired constructor(
    private val mapper: ObjectMapper,
) {

    @Test
    fun `말투를 주지 않으면 행에도 적지 않는다`() {
        val option: GenerationOption = AudioGenerateOption(text = "질문", voice = "Kore")

        val json = mapper.writeValueAsString(option)

        assertEquals("""{"type":"audio-generate","text":"질문","voice":"Kore"}""", json)
        assertEquals(option, mapper.readValue<GenerationOption>(json))
        assertEquals(emptyList(), option.fileSources())
    }

    @Test
    fun `말투와 함께 왕복한다`() {
        val option: GenerationOption = AudioGenerateOption(text = "질문", voice = "Kore", style = "차분하게")

        val json = mapper.writeValueAsString(option)

        assertEquals("""{"type":"audio-generate","text":"질문","voice":"Kore","style":"차분하게"}""", json)
        assertEquals(option, mapper.readValue<GenerationOption>(json))
    }
}
