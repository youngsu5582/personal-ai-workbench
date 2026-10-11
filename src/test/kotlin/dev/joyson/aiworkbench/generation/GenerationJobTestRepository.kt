package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.generation.domain.GenerationJob
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** 테스트가 쓰는 전체 CRUD. 따로 두는 이유는 [GenerationJobTaskTestRepository] 와 같다. */
interface GenerationJobTestRepository : JpaRepository<GenerationJob, Long> {

    fun findByUuid(uuid: UUID): GenerationJob?
}
