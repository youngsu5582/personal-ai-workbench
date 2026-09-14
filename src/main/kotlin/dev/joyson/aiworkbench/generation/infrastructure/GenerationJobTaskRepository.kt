package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GenerationJobTaskRepository : JpaRepository<GenerationJobTask, Long> {
}