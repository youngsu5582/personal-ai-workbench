package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.GenerationJob
import org.springframework.data.jpa.repository.JpaRepository

interface GenerationJobRepository : JpaRepository<GenerationJob, Long> {
}