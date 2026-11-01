package dev.joyson.aiworkbench.auth.domain

import org.springframework.data.repository.Repository
import java.util.UUID

interface ServiceApiKeyRepository : Repository<ServiceApiKey, Long> {
    fun save(key: ServiceApiKey): ServiceApiKey
    fun findAllByWorkspaceUuidOrderByCreatedAtAsc(workspaceUuid: UUID): List<ServiceApiKey>
    fun findByUuidAndWorkspaceUuid(uuid: UUID, workspaceUuid: UUID): ServiceApiKey?
}
