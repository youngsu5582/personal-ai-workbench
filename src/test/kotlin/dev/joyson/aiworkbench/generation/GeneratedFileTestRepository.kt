package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import org.springframework.data.jpa.repository.JpaRepository

/** 테스트가 쓰는 전체 CRUD. 따로 두는 이유는 [GenerationJobTaskTestRepository] 와 같다. */
interface GeneratedFileTestRepository : JpaRepository<GeneratedFile, Long> {

    fun findAllByTaskId(taskId: Long): List<GeneratedFile>
}
