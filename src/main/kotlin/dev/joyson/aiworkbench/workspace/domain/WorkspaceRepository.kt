package dev.joyson.aiworkbench.workspace.domain

import org.springframework.data.repository.Repository
import java.util.UUID

interface WorkspaceRepository : Repository<Workspace, Long> {
    fun save(workspace: Workspace): Workspace
    fun findAllByOwnerUserUuidOrderByCreatedAtAsc(ownerUserUuid: UUID): List<Workspace>
    fun findByUuidAndOwnerUserUuid(uuid: UUID, ownerUserUuid: UUID): Workspace?
}
