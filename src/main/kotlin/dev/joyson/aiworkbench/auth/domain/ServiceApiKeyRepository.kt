package dev.joyson.aiworkbench.auth.domain

import org.springframework.data.repository.Repository
import java.util.UUID

/** 키 조회는 서비스 범위로 제한한다. 서비스 소유 여부는 호출자가 별도로 확인한다. */
interface ServiceApiKeyRepository : Repository<ServiceApiKey, Long> {
    fun save(key: ServiceApiKey): ServiceApiKey
    fun findAllByWorkspaceUuidOrderByCreatedAtAsc(workspaceUuid: UUID): List<ServiceApiKey>
    fun findByUuidAndWorkspaceUuid(uuid: UUID, workspaceUuid: UUID): ServiceApiKey?
}
