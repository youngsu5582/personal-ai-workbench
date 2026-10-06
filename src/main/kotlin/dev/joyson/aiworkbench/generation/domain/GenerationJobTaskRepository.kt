package dev.joyson.aiworkbench.generation.domain

interface GenerationJobTaskRepository {

    fun saveAll(tasks: List<GenerationJobTask>): List<GenerationJobTask>

    fun findByIdOrNull(id: Long): GenerationJobTask?

    fun findAllByJobIdOrderBySequence(jobId: Long): List<GenerationJobTask>

    /** 오래 기다린 것부터 집도록 id 순으로 [limit] 개를 준다. 개수는 부르는 쪽이 정한다. */
    fun findOldestByStatus(status: TaskStatus, limit: Int): List<GenerationJobTask>
}
