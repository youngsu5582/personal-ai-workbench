package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import org.springframework.data.jpa.repository.JpaRepository

interface GeneratedFileRepository : JpaRepository<GeneratedFile, Long> {

    fun findAllByTaskId(taskId: Long): List<GeneratedFile>

    /** Task 하나당 한 번씩 묻지 않으려고 한 번에 가져온다. */
    fun findAllByTaskIdIn(taskIds: Collection<Long>): List<GeneratedFile>
}
