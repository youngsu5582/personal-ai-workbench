package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.generation.application.GenerationJobService
import dev.joyson.aiworkbench.generation.application.GenerationTemplateService
import dev.joyson.aiworkbench.generation.application.InvalidTemplateVariablesException
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import dev.joyson.aiworkbench.workspace.application.WorkspaceService
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
    private val users: UserRegistry,
    private val workspaces: WorkspaceService,
) : IntegrationTest() {
    private fun newOwner(): UUID = users.resolveOrRegister(
        RegisterIdentityCommand.of("https://accounts.google.com", UUID.randomUUID().toString(), "소유자", "owner@example.com"),
    ).uuid
    @Test
    fun `템플릿 변경과 삭제 후에도 접수된 작업은 완성된 입력을 보관한다`() {
        val owner = newOwner()
        val workspace = assertNotNull(workspaces.findDefaultUuid(owner))
        val template = templates.create(owner, "요약", "너는 {{role}}이다. {{role}}처럼 답해라", workspace)
        val job = assertNotNull(templates.submit(owner, template.uuid, mapOf("role" to "편집자"), "요약해", SharedTestConfig.FAKE_TEXT_MODEL, 1, workspace))
        templates.update(owner, template.uuid, "변경", "새 지시", workspace)
        templates.delete(owner, template.uuid, workspace)
        val option = jobs.findOwned(job.uuid, workspace)?.option as TextGenerateOption
        assertEquals("너는 편집자이다. 편집자처럼 답해라", option.instructions)
        assertEquals("요약해", option.prompt)
    }

    @Test
    fun `변수 누락과 추가는 거절하고 다른 사용자는 템플릿을 볼 수 없다`() {
        val owner = newOwner()
        val other = newOwner()
        val workspace = assertNotNull(workspaces.findDefaultUuid(owner))
        val otherWorkspace = assertNotNull(workspaces.findDefaultUuid(other))
        val template = templates.create(owner, "역할", "너는 {{role}}이다", workspace)
        assertNull(templates.find(other, template.uuid, otherWorkspace))
        assertNull(templates.submit(other, template.uuid, emptyMap(), "질문", SharedTestConfig.FAKE_TEXT_MODEL, 1, otherWorkspace))
        assertFailsWith<InvalidTemplateVariablesException> {
            templates.submit(owner, template.uuid, emptyMap(), "질문", SharedTestConfig.FAKE_TEXT_MODEL, 1, workspace)
        }
        assertFailsWith<InvalidTemplateVariablesException> {
            templates.submit(owner, template.uuid, mapOf("role" to "편집자", "extra" to "값"), "질문", SharedTestConfig.FAKE_TEXT_MODEL, 1, workspace)
        }
    }

    @Test
    fun `같은 사용자라도 서비스가 다르면 템플릿을 조회하거나 실행할 수 없다`() {
        val owner = newOwner()
        val serviceA = workspaces.create(owner, "A").uuid
        val serviceB = workspaces.create(owner, "B").uuid
        val template = assertNotNull(templates.create(owner, "역할", "너는 편집자다", serviceA))
        assertNotNull(templates.find(owner, template.uuid, serviceA))
        assertNull(templates.find(owner, template.uuid, serviceB))
        assertNull(templates.find(owner, template.uuid, assertNotNull(workspaces.findDefaultUuid(owner))))
        assertEquals(emptyList(), templates.list(owner, serviceB))
        assertNull(templates.update(owner, template.uuid, "변경", "변경", serviceB))
        assertEquals(false, templates.delete(owner, template.uuid, serviceB))
        assertNull(templates.submit(owner, template.uuid, emptyMap(), "질문", SharedTestConfig.FAKE_TEXT_MODEL, 1, serviceB))
        val job = assertNotNull(templates.submit(owner, template.uuid, emptyMap(), "질문", SharedTestConfig.FAKE_TEXT_MODEL, 1, serviceA))
        assertNotNull(jobs.findOwned(job.uuid, serviceA))
        assertNull(jobs.findOwned(job.uuid, serviceB))
    }
}
