package dev.joyson.aiworkbench.workspace.domain

import org.springframework.data.repository.Repository
import java.util.UUID

/** 상세 조회와 수정은 서비스 식별자와 소유자를 함께 조건으로 사용한다. */
interface WorkspaceRepository : Repository<Workspace, Long> {
    fun save(workspace: Workspace): Workspace
    fun findAllByOwnerUserUuidOrderByCreatedAtAsc(ownerUserUuid: UUID): List<Workspace>
    fun findByUuidAndOwnerUserUuid(uuid: UUID, ownerUserUuid: UUID): Workspace?
}
