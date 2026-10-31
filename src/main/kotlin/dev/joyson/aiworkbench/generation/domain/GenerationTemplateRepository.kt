package dev.joyson.aiworkbench.generation.domain

import org.springframework.data.repository.Repository
import java.util.UUID

interface GenerationTemplateRepository : Repository<GenerationTemplate, Long> {
    fun save(template: GenerationTemplate): GenerationTemplate
    fun findByUuidAndOwnerUserUuid(uuid: UUID, ownerUserUuid: UUID): GenerationTemplate?
    fun findAllByOwnerUserUuidOrderByCreatedAtDesc(ownerUserUuid: UUID): List<GenerationTemplate>
    fun delete(template: GenerationTemplate)
}
