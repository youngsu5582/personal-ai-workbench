package dev.joyson.aiworkbench.generation.domain

import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.DecisionChoice
import dev.joyson.aiworkbench.generation.domain.option.DecisionEvidence
import dev.joyson.aiworkbench.generation.domain.option.DecisionOption
import dev.joyson.aiworkbench.generation.domain.option.DecisionQuestion
import dev.joyson.aiworkbench.generation.domain.option.FileSource
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.generation.domain.option.ImageOption
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TemplateSpecTest {

    private val reference = FileSource.Uploaded("a".repeat(64))
    private val photo = FileSource.Uploaded("b".repeat(64))
    private val size = ImageSize.ByRatio(AspectRatio.SIXTEEN_NINE, Resolution.ONE_K)

    private fun template(option: GenerationOption, inputImageCount: Int = 0) = TemplateSpec(option, inputImageCount)

    @Test
    fun `글 생성은 지시문과 프롬프트의 변수를 처음 나온 순서로 모으고 같은 이름은 한 값으로 채운다`() {
        val template = template(TextGenerateOption(instructions = "너는 {{role}}이다. {{role}}처럼 {{tone}} 답한다", prompt = "{{request}}"))

        assertEquals(listOf("role", "tone", "request"), template.variables.toList())
        val filled = assertIs<TextGenerateOption>(template.fill(mapOf("role" to "편집자", "tone" to "담백하게", "request" to "요약해"), emptyList()))
        assertEquals("너는 편집자이다. 편집자처럼 담백하게 답한다", filled.instructions)
        assertEquals("요약해", filled.prompt)
    }

    @Test
    fun `이미지 생성은 고정 참조를 유지하고 프롬프트만 채운다`() {
        val template = template(ImageOption(prompt = "이 글로 썸네일을 만들어라.\n{{content}}", size = size, sources = listOf(reference)))

        val filled = assertIs<ImageOption>(template.fill(mapOf("content" to "글 본문"), emptyList()))
        assertEquals("이 글로 썸네일을 만들어라.\n글 본문", filled.prompt)
        assertEquals(listOf(reference), filled.sources)
        assertEquals(size, filled.size)
    }

    @Test
    fun `이미지 생성은 실행 때 받은 이미지를 참조로 붙인다`() {
        val template = template(ImageOption(prompt = "입력한 이미지의 스타일을 바꿔라. {{style}}", size = size), inputImageCount = 1)

        val filled = assertIs<ImageOption>(template.fill(mapOf("style" to "수채화"), listOf(photo)))
        assertEquals(listOf(photo), filled.sources)
    }

    @Test
    fun `고정 참조와 입력 이미지가 합쳐 상한을 넘는 템플릿은 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            template(ImageOption(prompt = "섞어라", size = size, sources = listOf(reference)), inputImageCount = 1)
        }
    }

    @Test
    fun `글 생성은 입력 이미지를 받지 않는다`() {
        assertFailsWith<IllegalArgumentException> { template(TextGenerateOption(prompt = "요약해"), inputImageCount = 1) }
    }

    @Test
    fun `판정은 근거 글과 질문 설명을 채우고 이름과 선택지 값은 그대로 둔다`() {
        val template = template(
            DecisionOption(
                input = listOf(DecisionEvidence.Text("{{review}}")),
                questions = listOf(
                    DecisionQuestion.Choice(
                        name = "tone_{{x}}",
                        instructions = "{{policy}} 기준으로 고른다",
                        choices = listOf(DecisionChoice("ok_{{x}}", "{{policy}} 를 지킨다"), DecisionChoice("bad", "어긴다")),
                    ),
                ),
            ),
            inputImageCount = 1,
        )

        assertEquals(listOf("review", "policy"), template.variables.toList())
        val filled = assertIs<DecisionOption>(template.fill(mapOf("review" to "좋아요", "policy" to "운영 정책"), listOf(photo)))
        val question = assertIs<DecisionQuestion.Choice>(filled.questions.single())
        assertEquals("tone_{{x}}", question.name)
        assertEquals("운영 정책 기준으로 고른다", question.instructions)
        assertEquals(listOf("ok_{{x}}", "bad"), question.choices.map { it.value })
        assertEquals("운영 정책 를 지킨다", question.choices.first().description)
        // 실행 때 받은 이미지는 템플릿의 근거 뒤에 온다. 순서가 곧 모델이 읽는 순서다.
        assertEquals(listOf(DecisionEvidence.Text("좋아요"), DecisionEvidence.Image(photo)), filled.input)
    }

    @Test
    fun `변수 키와 입력 이미지 수는 정확히 맞아야 한다`() {
        val template = template(ImageOption(prompt = "{{style}}", size = size), inputImageCount = 1)

        assertFailsWith<IllegalArgumentException> { template.fill(emptyMap(), listOf(photo)) }
        assertFailsWith<IllegalArgumentException> { template.fill(mapOf("style" to "수채화", "extra" to "값"), listOf(photo)) }
        assertFailsWith<IllegalArgumentException> { template.fill(mapOf("style" to "수채화"), emptyList()) }
        assertFailsWith<IllegalArgumentException> { template.fill(mapOf("style" to "수채화"), listOf(photo, photo)) }
    }

    @Test
    fun `값 안의 변수 표기는 다시 치환하지 않는다`() {
        val template = template(TextGenerateOption(prompt = "{{a}} {{b}}"))

        val filled = assertIs<TextGenerateOption>(template.fill(mapOf("a" to "{{b}}", "b" to "둘"), emptyList()))
        assertEquals("{{b}} 둘", filled.prompt)
    }

    @Test
    fun `치환 결과가 옵션 규칙을 어기면 거절한다`() {
        val template = template(TextGenerateOption(instructions = "{{tone}}", prompt = "{{request}}"))

        assertFailsWith<IllegalArgumentException> { template.fill(mapOf("tone" to "", "request" to "요약해"), emptyList()) }
        assertFailsWith<IllegalArgumentException> { template.fill(mapOf("tone" to "담백하게", "request" to " "), emptyList()) }
    }

    @Test
    fun `기본 모델은 비어 있을 수 없고 저장 표기에 계산 값이 들어가지 않는다`() {
        assertFailsWith<IllegalArgumentException> { TemplateSpec(TextGenerateOption(prompt = "요약해"), model = " ") }
        val spec = TemplateSpec(TextGenerateOption(prompt = "{{request}}"), model = "text-model")
        assertEquals(setOf("request"), spec.variables)
        assertEquals("text-model", spec.model)
    }

    @Test
    fun `템플릿의 종류는 명세를 바꿔도 바뀌지 않는다`() {
        val stored = GenerationTemplate(ownerUserUuid = UUID.randomUUID(), workspaceUuid = UUID.randomUUID(), name = "템플릿",
            spec = TemplateSpec(TextGenerateOption(prompt = "요약해")))

        assertFailsWith<IllegalArgumentException> { stored.replace("변경", TemplateSpec(ImageOption(prompt = "그려라", size = size))) }
        stored.replace("변경", TemplateSpec(TextGenerateOption(prompt = "번역해"), model = "text-model"))
        assertEquals("변경", stored.name)
        assertEquals("text-model", stored.spec.model)
    }
}
