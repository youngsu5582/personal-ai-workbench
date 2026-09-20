package dev.joyson.aiworkbench.usage.infrastructure

import dev.joyson.aiworkbench.usage.domain.ProviderCall
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProviderCallRepository : JpaRepository<ProviderCall, Long> {

    fun findAllByTaskUuid(taskUuid: UUID): List<ProviderCall>

    fun findAllByJobUuid(jobUuid: UUID): List<ProviderCall>
}
