package dev.joyson.aiworkbench.workspace.application

import dev.joyson.aiworkbench.workspace.WorkspaceRegistry
import dev.joyson.aiworkbench.workspace.domain.Workspace
import dev.joyson.aiworkbench.workspace.domain.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WorkspaceService(private val repository: WorkspaceRepository) : WorkspaceRegistry {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun createDefault(ownerUuid: UUID) {
        repository.save(Workspace(ownerUserUuid = ownerUuid, name = "기본 서비스", isDefault = true))
    }

    @Transactional
    fun create(owner: UUID, name: String): Workspace =
        repository.save(Workspace(ownerUserUuid = owner, name = name))

    @Transactional(readOnly = true)
    fun list(owner: UUID): List<Workspace> = repository.findAllByOwnerUserUuidOrderByCreatedAtAsc(owner)

    @Transactional(readOnly = true)
    fun find(owner: UUID, uuid: UUID): Workspace? = repository.findByUuidAndOwnerUserUuid(uuid, owner)

    @Transactional
    fun rename(owner: UUID, uuid: UUID, name: String): Workspace? =
        repository.findByUuidAndOwnerUserUuid(uuid, owner)?.apply { this.name = name }
}
