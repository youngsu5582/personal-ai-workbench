package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface GenerationJobTaskRepository : JpaRepository<GenerationJobTask, Long> {

    fun findAllByJobIdOrderBySequence(jobId: Long): List<GenerationJobTask>

    /** 오래 기다린 것부터 집도록 id 순으로 준다. 개수는 부르는 쪽이 정한다. */
    fun findByStatusOrderByIdAsc(status: TaskStatus, pageable: Pageable): List<GenerationJobTask>
}
