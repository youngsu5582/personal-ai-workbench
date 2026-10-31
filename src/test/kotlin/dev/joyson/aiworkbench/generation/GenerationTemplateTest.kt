package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.generation.application.GenerationJobService
import dev.joyson.aiworkbench.generation.application.GenerationTemplateService
import dev.joyson.aiworkbench.generation.application.InvalidTemplateVariablesException
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GenerationTemplateTest @Autowired constructor(
    private val templates: GenerationTemplateService,
    private val jobs: GenerationJobService,
) : IntegrationTest() {
    @Test
    fun `템플릿 변경과 삭제 후에도 접수된 작업은 완성된 입력을 보관한다`() {
        val owner = UUID.randomUUID()
        val template = templates.create(owner, "요약", "너는 {{role}}이다. {{role}}처럼 답해라")
        val job = assertNotNull(templates.submit(owner, template.uuid, mapOf("role" to "편집자"), "요약해", SharedTestConfig.FAKE_TEXT_MODEL, 1))
        templates.update(owner, template.uuid, "변경", "새 지시")
        templates.delete(owner, template.uuid)
        val option = jobs.findOwned(job.uuid, owner)?.option as TextGenerateOption
        assertEquals("너는 편집자이다. 편집자처럼 답해라", option.instructions)
        assertEquals("요약해", option.prompt)
    }

    @Test
    fun `변수 누락과 추가는 거절하고 다른 사용자는 템플릿을 볼 수 없다`() {
        val owner = UUID.randomUUID()
        val other = UUID.randomUUID()
        val template = templates.create(owner, "역할", "너는 {{role}}이다")
        assertNull(templates.find(other, template.uuid))
        assertNull(templates.submit(other, template.uuid, emptyMap(), "질문", SharedTestConfig.FAKE_TEXT_MODEL, 1))
        assertFailsWith<InvalidTemplateVariablesException> {
            templates.submit(owner, template.uuid, emptyMap(), "질문", SharedTestConfig.FAKE_TEXT_MODEL, 1)
        }
        assertFailsWith<InvalidTemplateVariablesException> {
            templates.submit(owner, template.uuid, mapOf("role" to "편집자", "extra" to "값"), "질문", SharedTestConfig.FAKE_TEXT_MODEL, 1)
        }
    }
}
