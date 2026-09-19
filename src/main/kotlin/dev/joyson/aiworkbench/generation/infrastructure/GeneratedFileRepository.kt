package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import org.springframework.data.jpa.repository.JpaRepository

interface GeneratedFileRepository : JpaRepository<GeneratedFile, Long> {

    fun findAllByTaskIdOrderBySequence(taskId: Long): List<GeneratedFile>
}
