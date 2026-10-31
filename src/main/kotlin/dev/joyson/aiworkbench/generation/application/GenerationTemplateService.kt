package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.GenerationTemplate
import dev.joyson.aiworkbench.generation.domain.GenerationTemplateRepository
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GenerationTemplateService(
    private val repository: GenerationTemplateRepository,
    private val generationJobService: GenerationJobService,
) {
    @Transactional
    fun create(owner: UUID, name: String, instructions: String): GenerationTemplate =
        repository.save(GenerationTemplate(ownerUserUuid = owner, name = name, instructions = instructions))

    @Transactional(readOnly = true)
    fun find(owner: UUID, uuid: UUID): GenerationTemplate? = repository.findByUuidAndOwnerUserUuid(uuid, owner)

    @Transactional(readOnly = true)
    fun list(owner: UUID): List<GenerationTemplate> = repository.findAllByOwnerUserUuidOrderByCreatedAtDesc(owner)

    @Transactional
    fun update(owner: UUID, uuid: UUID, name: String, instructions: String): GenerationTemplate? =
        repository.findByUuidAndOwnerUserUuid(uuid, owner)?.apply {
            this.name = name
            this.instructions = instructions
        }

    @Transactional
    fun delete(owner: UUID, uuid: UUID): Boolean {
        val template = repository.findByUuidAndOwnerUserUuid(uuid, owner) ?: return false
        repository.delete(template)
        return true
    }

    fun submit(owner: UUID, uuid: UUID, variables: Map<String, String>, prompt: String, model: String, taskCount: Int): GenerationJobView? {
        val template = find(owner, uuid) ?: return null
        val instructions = renderInstructions(template.instructions, variables)
        return generationJobService.submit(owner, GenerationCommand(TextGenerateOption(prompt = prompt, instructions = instructions), model, taskCount))
    }
}

internal fun renderInstructions(template: String, variables: Map<String, String>): String {
    val pattern = Regex("\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)}}")
    val names = pattern.findAll(template).map { it.groupValues[1] }.toSet()
    if (names != variables.keys) throw InvalidTemplateVariablesException("필요한 변수와 전달된 변수가 일치해야 한다")
    val rendered = pattern.replace(template) { variables.getValue(it.groupValues[1]) }
    if (rendered.isBlank()) throw InvalidTemplateVariablesException("완성된 시스템 프롬프트는 비어 있을 수 없다")
    return rendered
}
