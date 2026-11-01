package dev.joyson.aiworkbench.generation.domain

import org.springframework.data.repository.Repository
import java.util.UUID

/** 템플릿을 저장하고, 인증된 사용자와 서비스 범위 안에서 조회한다. */
interface GenerationTemplateRepository : Repository<GenerationTemplate, Long> {
    fun save(template: GenerationTemplate): GenerationTemplate

    /** 다른 사용자나 다른 서비스의 템플릿은 없는 것처럼 `null`을 반환한다. */
    fun findByUuidAndOwnerUserUuidAndWorkspaceUuid(uuid: UUID, ownerUserUuid: UUID, workspaceUuid: UUID): GenerationTemplate?

    /** 해당 범위의 템플릿을 생성 시각 내림차순으로 반환한다. */
    fun findAllByOwnerUserUuidAndWorkspaceUuidOrderByCreatedAtDesc(ownerUserUuid: UUID, workspaceUuid: UUID): List<GenerationTemplate>

    fun delete(template: GenerationTemplate)
}
