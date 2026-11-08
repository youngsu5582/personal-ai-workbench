package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.generation.application.GenerationJobService
import dev.joyson.aiworkbench.generation.application.GenerationTemplateService
import dev.joyson.aiworkbench.generation.application.InvalidTemplateException
import dev.joyson.aiworkbench.generation.application.UnknownFileSourceException
import dev.joyson.aiworkbench.generation.application.UnsupportedModelException
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.DecisionEvidence
import dev.joyson.aiworkbench.generation.domain.option.DecisionOption
import dev.joyson.aiworkbench.generation.domain.option.DecisionQuestion
import dev.joyson.aiworkbench.generation.domain.option.FileSource
import dev.joyson.aiworkbench.generation.domain.option.ImageOption
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.Resolution
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

    private val text = TextGenerateOption(instructions = "너는 {{role}}이다. {{role}}처럼 답해라", prompt = "{{request}}")
    private val image = ImageOption(prompt = "{{style}} 로 바꿔라", size = ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K))

    @Test
    fun `템플릿 변경과 삭제 후에도 접수된 작업은 완성된 입력을 보관한다`() {
        val owner = newOwner()
        val workspace = assertNotNull(workspaces.findDefaultUuid(owner))
        val template = templates.create(owner, "요약", text, 0, workspace)
        val job = assertNotNull(
            templates.submit(owner, template.uuid, mapOf("role" to "편집자", "request" to "요약해"), emptyList(), SharedTestConfig.FAKE_TEXT_MODEL, 1, workspace),
        )
        templates.update(owner, template.uuid, "변경", TextGenerateOption(prompt = "새 요청"), 0, workspace)
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
        val template = templates.create(owner, "역할", text, 0, workspace)
        val filled = mapOf("role" to "편집자", "request" to "요약해")
        assertNull(templates.find(other, template.uuid, otherWorkspace))
        assertNull(templates.submit(other, template.uuid, filled, emptyList(), SharedTestConfig.FAKE_TEXT_MODEL, 1, otherWorkspace))
        assertFailsWith<InvalidTemplateException> {
            templates.submit(owner, template.uuid, mapOf("role" to "편집자"), emptyList(), SharedTestConfig.FAKE_TEXT_MODEL, 1, workspace)
        }
        assertFailsWith<InvalidTemplateException> {
            templates.submit(owner, template.uuid, filled + ("extra" to "값"), emptyList(), SharedTestConfig.FAKE_TEXT_MODEL, 1, workspace)
        }
    }

    @Test
    fun `같은 사용자라도 서비스가 다르면 템플릿을 조회하거나 실행할 수 없다`() {
        val owner = newOwner()
        val serviceA = workspaces.create(owner, "A").uuid
        val serviceB = workspaces.create(owner, "B").uuid
        val fixed = TextGenerateOption(prompt = "요약해")
        val template = assertNotNull(templates.create(owner, "역할", fixed, 0, serviceA))
        assertNotNull(templates.find(owner, template.uuid, serviceA))
        assertNull(templates.find(owner, template.uuid, serviceB))
        assertNull(templates.find(owner, template.uuid, assertNotNull(workspaces.findDefaultUuid(owner))))
        assertEquals(emptyList(), templates.list(owner, serviceB))
        assertNull(templates.update(owner, template.uuid, "변경", fixed, 0, serviceB))
        assertEquals(false, templates.delete(owner, template.uuid, serviceB))
        assertNull(templates.submit(owner, template.uuid, emptyMap(), emptyList(), SharedTestConfig.FAKE_TEXT_MODEL, 1, serviceB))
        val job = assertNotNull(templates.submit(owner, template.uuid, emptyMap(), emptyList(), SharedTestConfig.FAKE_TEXT_MODEL, 1, serviceA))
        assertNotNull(jobs.findOwned(job.uuid, serviceA))
        assertNull(jobs.findOwned(job.uuid, serviceB))
    }

    @Test
    fun `종류를 바꾸거나 받을 수 없는 이미지 수로 저장하면 거절한다`() {
        val owner = newOwner()
        val workspace = assertNotNull(workspaces.findDefaultUuid(owner))
        val template = templates.create(owner, "요약", text, 0, workspace)

        assertFailsWith<InvalidTemplateException> { templates.update(owner, template.uuid, "그림", image, 0, workspace) }
        assertFailsWith<InvalidTemplateException> { templates.create(owner, "글", text, 1, workspace) }
        assertFailsWith<InvalidTemplateException> { templates.create(owner, "그림", image, 2, workspace) }
    }

    @Test
    fun `실행 요청이 모델을 주지 않으면 템플릿의 기본 모델을 쓰고 주면 그것을 쓴다`() {
        val owner = newOwner()
        val workspace = assertNotNull(workspaces.findDefaultUuid(owner))
        val fixed = TextGenerateOption(prompt = "요약해")
        val withModel = templates.create(owner, "기본 모델", fixed, 0, workspace, SharedTestConfig.FAKE_TEXT_MODEL)
        val withoutModel = templates.create(owner, "모델 없음", fixed, 0, workspace)

        val byDefault = assertNotNull(templates.submit(owner, withModel.uuid, emptyMap(), emptyList(), null, 1, workspace))
        assertEquals(SharedTestConfig.FAKE_TEXT_MODEL, jobs.findOwned(byDefault.uuid, workspace)?.model)
        assertFailsWith<InvalidTemplateException> { templates.submit(owner, withoutModel.uuid, emptyMap(), emptyList(), null, 1, workspace) }
        val chosen = assertNotNull(templates.submit(owner, withoutModel.uuid, emptyMap(), emptyList(), SharedTestConfig.FAKE_TEXT_MODEL, 1, workspace))
        assertEquals(SharedTestConfig.FAKE_TEXT_MODEL, jobs.findOwned(chosen.uuid, workspace)?.model)
    }

    @Test
    fun `기본 모델은 템플릿 종류를 다룰 수 있어야 한다`() {
        val owner = newOwner()
        val workspace = assertNotNull(workspaces.findDefaultUuid(owner))
        assertFailsWith<UnsupportedModelException> { templates.create(owner, "글", text, 0, workspace, SharedTestConfig.FAKE_MODEL) }
        assertFailsWith<UnsupportedModelException> { templates.create(owner, "그림", image, 0, workspace, "unknown-model") }
        val template = templates.create(owner, "그림", image, 0, workspace, SharedTestConfig.FAKE_MODEL)
        assertFailsWith<UnsupportedModelException> { templates.update(owner, template.uuid, "그림", image, 0, workspace, SharedTestConfig.FAKE_TEXT_MODEL) }
    }

    @Test
    fun `입력 이미지도 서비스 소유를 확인하고 판정은 작업 하나만 받는다`() {
        val owner = newOwner()
        val workspace = assertNotNull(workspaces.findDefaultUuid(owner))
        val restyle = templates.create(owner, "스타일 수정", image, 1, workspace)
        val unknown = FileSource.Uploaded("c".repeat(64))
        assertFailsWith<UnknownFileSourceException> {
            templates.submit(owner, restyle.uuid, mapOf("style" to "수채화"), listOf(unknown), SharedTestConfig.FAKE_MODEL, 1, workspace)
        }

        val decision = DecisionOption(
            input = listOf(DecisionEvidence.Text("{{review}}")),
            questions = listOf(DecisionQuestion.Predicate("abusive", "욕설이 있는가")),
        )
        val judge = templates.create(owner, "판정", decision, 0, workspace)
        assertFailsWith<InvalidTemplateException> {
            templates.submit(owner, judge.uuid, mapOf("review" to "좋아요"), emptyList(), SharedTestConfig.FAKE_DECISION_MODEL, 2, workspace)
        }
        assertNotNull(templates.submit(owner, judge.uuid, mapOf("review" to "좋아요"), emptyList(), SharedTestConfig.FAKE_DECISION_MODEL, 1, workspace))
    }
}
