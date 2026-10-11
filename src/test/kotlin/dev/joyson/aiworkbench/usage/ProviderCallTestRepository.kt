package dev.joyson.aiworkbench.usage

import dev.joyson.aiworkbench.usage.domain.ProviderCall
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * 테스트가 쓰는 전체 CRUD.
 * 따로 두는 이유는 [dev.joyson.aiworkbench.generation.GenerationJobTaskTestRepository] 와 같다.
 */
interface ProviderCallTestRepository : JpaRepository<ProviderCall, Long> {

    fun findAllByTaskUuid(taskUuid: UUID): List<ProviderCall>
}
