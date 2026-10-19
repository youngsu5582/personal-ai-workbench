package dev.joyson.aiworkbench.generation.domain.option

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.DatabindException
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 글 생성 옵션의 저장 표기를 고정한다. [FileSourceTest] 와 같은 이유로 애플리케이션이 구성한 매퍼를 쓴다.
 *
 * 여기 적힌 JSON 문자열이 곧 DB 계약이다. 바꾸려면 기존 행을 고쳐야 한다.
 */
@ActiveProfiles("test")
@JsonTest
class TextGenerateOptionTest @Autowired constructor(
    private val mapper: ObjectMapper,
) {

    private val schema = """{"type":"object","properties":{"reasoning":{"type":"string"},"answer":{"type":"string"}}}"""

    @Test
    fun `형식을 주지 않으면 형식 없는 글이다`() {
        val option = mapper.readValue<GenerationOption>("""{"type":"text-generate","prompt":"요약해"}""")

        assertIs<TextGenerateOption>(option)
        assertEquals(TextFormat.Text, option.output)
    }

    @Test
    fun `형식 없는 글 옵션을 왕복시킨다`() {
        val option: GenerationOption = TextGenerateOption(prompt = "요약해", instructions = "편집자다")

        val json = mapper.writeValueAsString(option)

        assertEquals(
            """{"type":"text-generate","prompt":"요약해","instructions":"편집자다","output":{"type":"text"}}""",
            json,
        )
        assertEquals(option, mapper.readValue<GenerationOption>(json))
    }

    @Test
    fun `effort 는 소문자 표기로 적히고 주지 않으면 없다`() {
        val option = mapper.readValue<GenerationOption>("""{"type":"text-generate","prompt":"요약해","effort":"low"}""")

        assertEquals(Effort.LOW, assertIs<TextGenerateOption>(option).effort)
        assertTrue(mapper.writeValueAsString(option).contains(""""effort":"low""""))
        // 주지 않은 값은 행에 남지 않는다.
        assertEquals("""{"type":"text-generate","prompt":"요약해","output":{"type":"text"}}""", mapper.writeValueAsString(TextGenerateOption(prompt = "요약해")))
    }

    /**
     * 요청은 스키마를 객체로 보낸다. 받은 순서가 문자열에 그대로 남아야 한다 —
     * 출력 키가 그 순서대로 나온다.
     */
    @Test
    fun `객체로 받은 스키마를 받은 순서 그대로 문자열로 든다`() {
        val option = mapper.readValue<GenerationOption>(
            """{"type":"text-generate","prompt":"풀어","output":{"type":"json-schema","schema":$schema}}""",
        )

        val output = assertIs<TextFormat.JsonSchema>(assertIs<TextGenerateOption>(option).output)
        assertEquals(schema, output.schema)
    }

    /** 저장된 행에는 문자열로 적힌다. jsonb 는 문자열 안을 건드리지 않는다. */
    @Test
    fun `스키마는 문자열로 적히고 그대로 되읽힌다`() {
        val option: GenerationOption = TextGenerateOption(prompt = "풀어", output = TextFormat.JsonSchema(schema))

        val json = mapper.writeValueAsString(option)

        assertTrue(json.contains(""""output":{"type":"json-schema","schema":${mapper.writeValueAsString(schema)}}"""), json)
        assertEquals(option, mapper.readValue<GenerationOption>(json))
    }

    @Test
    fun `객체가 아닌 스키마는 받지 않는다`() {
        assertFailsWith<DatabindException> {
            mapper.readValue<GenerationOption>(
                """{"type":"text-generate","prompt":"풀어","output":{"type":"json-schema","schema":[1,2]}}""",
            )
        }
        assertFailsWith<IllegalArgumentException> { TextFormat.JsonSchema("이건 JSON 이 아니다") }
    }

    @Test
    fun `빈 instructions 는 빼라고 알린다`() {
        assertFailsWith<IllegalArgumentException> { TextGenerateOption(prompt = "요약해", instructions = " ") }
        assertFailsWith<IllegalArgumentException> { TextGenerateOption(prompt = " ") }
    }

    @Test
    fun `스키마를 준 형식은 JSON 객체만 받아들인다`() {
        val format = TextFormat.JsonSchema(schema)

        assertTrue(format.conforms("""{"reasoning":"","answer":"42"}"""))
        assertFalse(format.conforms("""{"answer":"4"""), "잘린 JSON 을 받아들였다")
        assertFalse(format.conforms("42"))
        assertTrue(TextFormat.Text.conforms("아무 글"))
    }
}
