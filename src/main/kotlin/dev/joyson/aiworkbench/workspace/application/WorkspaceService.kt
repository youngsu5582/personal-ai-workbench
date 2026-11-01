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
    /**
     * 호출자의 트랜잭션이 없으면 실패한다. 사용자 등록과 기본 서비스 생성이 함께 롤백되도록 한다.
     * 사용자당 기본 서비스의 중복은 DB 의 부분 유니크 인덱스가 막는다.
     */
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
