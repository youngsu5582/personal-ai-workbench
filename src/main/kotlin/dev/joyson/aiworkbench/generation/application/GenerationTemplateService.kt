package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.GenerationTemplate
import dev.joyson.aiworkbench.generation.domain.GenerationTemplateRepository
import dev.joyson.aiworkbench.generation.domain.TemplateSpec
import dev.joyson.aiworkbench.generation.domain.option.DecisionOption
import dev.joyson.aiworkbench.generation.domain.option.FileSource
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** 서비스별 템플릿을 관리하고, 변수와 입력 이미지로 완성한 요청을 기존 생성 접수 흐름으로 넘긴다. */
@Service
class GenerationTemplateService(
    private val repository: GenerationTemplateRepository,
    private val generationJobService: GenerationJobService,
) {
    /** 인증된 사용자와 선택된 서비스에 새 템플릿을 저장한다. 기본 모델은 이 종류를 다룰 수 있어야 한다. */
    @Transactional
    fun create(owner: UUID, name: String, option: GenerationOption, inputImageCount: Int, workspace: UUID, model: String? = null): GenerationTemplate {
        val spec = specOf(option, inputImageCount, model)
        return repository.save(GenerationTemplate(ownerUserUuid = owner, workspaceUuid = workspace, name = name, spec = spec))
    }

    /** 해당 사용자와 서비스의 템플릿만 찾는다. 범위 밖이거나 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun find(owner: UUID, uuid: UUID, workspace: UUID): GenerationTemplate? =
        repository.findByUuidAndOwnerUserUuidAndWorkspaceUuid(uuid, owner, workspace)

    /** 선택한 서비스의 템플릿을 생성 시각 내림차순으로 조회한다. */
    @Transactional(readOnly = true)
    fun list(owner: UUID, workspace: UUID): List<GenerationTemplate> =
        repository.findAllByOwnerUserUuidAndWorkspaceUuidOrderByCreatedAtDesc(owner, workspace)

    /** 이름 · 옵션 · 입력 이미지 수 · 기본 모델을 교체한다. 범위 밖이거나 없으면 `null`이고, 종류를 바꾸려 하면 [InvalidTemplateException]이다. */
    @Transactional
    fun update(owner: UUID, uuid: UUID, name: String, option: GenerationOption, inputImageCount: Int, workspace: UUID, model: String? = null): GenerationTemplate? {
        val spec = specOf(option, inputImageCount, model)
        return find(owner, uuid, workspace)?.apply { invalidAsRequest { replace(name, spec) } }
    }

    /** 명세를 만든다. 기본 모델은 접수와 같은 규칙으로 그 종류를 다루는지 확인하고, 명세의 규칙을 어기면 400 이다. */
    private fun specOf(option: GenerationOption, inputImageCount: Int, model: String?): TemplateSpec {
        model?.let { generationJobService.requireSupported(option, it) }
        return invalidAsRequest { TemplateSpec(option, inputImageCount, model) }
    }

    /** 범위 안의 템플릿을 삭제한다. 삭제할 수 있는 템플릿이 없으면 `false`다. */
    @Transactional
    fun delete(owner: UUID, uuid: UUID, workspace: UUID): Boolean {
        val template = find(owner, uuid, workspace) ?: return false
        repository.delete(template)
        return true
    }

    /**
     * 템플릿의 변수를 채우고 입력 이미지를 붙여 생성 Job을 접수한다.
     *
     * 범위 밖이거나 없는 템플릿은 `null`이다. 완성한 옵션은 Job에 저장되므로 템플릿이 나중에
     * 바뀌어도 접수된 입력은 유지된다. 모델 지원 여부와 이미지 소유는 기존 생성 접수 서비스가 검증한다.
     * [model] 을 주지 않으면 템플릿의 기본 모델을 쓰고, 그것도 없으면 [InvalidTemplateException] 이다.
     */
    fun submit(
        owner: UUID,
        uuid: UUID,
        variables: Map<String, String>,
        images: List<FileSource>,
        model: String?,
        taskCount: Int,
        workspace: UUID,
    ): GenerationJobView? {
        val template = find(owner, uuid, workspace) ?: return null
        val chosen = model ?: template.spec.model ?: throw InvalidTemplateException("모델을 정해야 한다 — 이 템플릿에는 기본 모델이 없다")
        val option = invalidAsRequest { template.spec.fill(variables, images) }
        // 판정 호출 한 번이 질문을 모두 답한다. Job 의 같은 불변식은 최후 방어라 거기서 걸리면 500 이 된다.
        if (option is DecisionOption && taskCount != 1) throw InvalidTemplateException("판정은 taskCount 가 1이어야 한다")
        return generationJobService.submit(owner, workspace, GenerationCommand(option, chosen, taskCount))
    }
}

/** 템플릿 도메인이 사용자 입력 때문에 거절한 것을 400 으로 응답할 예외로 바꾼다. */
private inline fun <T> invalidAsRequest(block: () -> T): T =
    try {
        block()
    } catch (e: IllegalArgumentException) {
        throw InvalidTemplateException(e.message ?: "템플릿 요청이 규칙에 맞지 않는다")
    }
