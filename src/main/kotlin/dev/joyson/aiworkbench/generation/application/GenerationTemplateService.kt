package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.GenerationTemplate
import dev.joyson.aiworkbench.generation.domain.GenerationTemplateRepository
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** 서비스별 템플릿을 관리하고, 변수 치환이 끝난 요청을 기존 생성 접수 흐름으로 넘긴다. */
@Service
class GenerationTemplateService(
    private val repository: GenerationTemplateRepository,
    private val generationJobService: GenerationJobService,
) {
    /** 인증된 사용자와 선택된 서비스에 새 템플릿을 저장한다. */
    @Transactional
    fun create(owner: UUID, name: String, systemPrompt: String, workspace: UUID): GenerationTemplate =
        repository.save(GenerationTemplate(ownerUserUuid = owner, workspaceUuid = workspace, name = name, systemPrompt = systemPrompt))

    /** 해당 사용자와 서비스의 템플릿만 찾는다. 범위 밖이거나 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun find(owner: UUID, uuid: UUID, workspace: UUID): GenerationTemplate? =
        repository.findByUuidAndOwnerUserUuidAndWorkspaceUuid(uuid, owner, workspace)

    /** 선택한 서비스의 템플릿을 생성 시각 내림차순으로 조회한다. */
    @Transactional(readOnly = true)
    fun list(owner: UUID, workspace: UUID): List<GenerationTemplate> =
        repository.findAllByOwnerUserUuidAndWorkspaceUuidOrderByCreatedAtDesc(owner, workspace)

    /** 이름과 시스템 지시문을 교체한다. 범위 밖이거나 없으면 `null`이다. */
    @Transactional
    fun update(owner: UUID, uuid: UUID, name: String, systemPrompt: String, workspace: UUID): GenerationTemplate? =
        find(owner, uuid, workspace)?.apply {
            this.name = name
            this.systemPrompt = systemPrompt
        }

    /** 범위 안의 템플릿을 삭제한다. 삭제할 수 있는 템플릿이 없으면 `false`다. */
    @Transactional
    fun delete(owner: UUID, uuid: UUID, workspace: UUID): Boolean {
        val template = find(owner, uuid, workspace) ?: return false
        repository.delete(template)
        return true
    }

    /**
     * 템플릿의 변수를 채워 텍스트 생성 Job을 접수한다.
     *
     * 범위 밖이거나 없는 템플릿은 `null`이다. 완성된 지시문과 사용자 프롬프트는 Job에
     * 저장되므로 템플릿이 나중에 바뀌어도 접수된 입력은 유지된다. 모델 지원 여부 등은
     * 기존 생성 접수 서비스가 검증한다.
     */
    fun submit(owner: UUID, uuid: UUID, variables: Map<String, String>, prompt: String, model: String, taskCount: Int, workspace: UUID): GenerationJobView? {
        val template = find(owner, uuid, workspace) ?: return null
        val instructions = renderInstructions(template.systemPrompt, variables)
        return generationJobService.submit(owner, workspace, GenerationCommand(TextGenerateOption(prompt = prompt, instructions = instructions), model, taskCount))
    }
}

/**
 * `{{name}}` 자리를 전달받은 문자열로 한 번 치환한다.
 *
 * 이름은 영문자 또는 밑줄로 시작하고 이후 영문자·숫자·밑줄만 허용한다. 같은 이름이
 * 여러 번 나타나도 값 하나를 공유하며, 인식한 이름과 전달된 키가 정확히 일치해야 한다.
 * 치환 결과가 공백뿐이면 [InvalidTemplateVariablesException]을 던진다.
 */
internal fun renderInstructions(template: String, variables: Map<String, String>): String {
    val pattern = Regex("\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)}}")
    val names = pattern.findAll(template).map { it.groupValues[1] }.toSet()
    if (names != variables.keys) throw InvalidTemplateVariablesException("필요한 변수와 전달된 변수가 일치해야 한다")
    val rendered = pattern.replace(template) { variables.getValue(it.groupValues[1]) }
    if (rendered.isBlank()) throw InvalidTemplateVariablesException("완성된 시스템 프롬프트는 비어 있을 수 없다")
    return rendered
}
