package dev.joyson.aiworkbench.generation.domain

import org.springframework.data.domain.Limit
import org.springframework.data.repository.Repository

/**
 * Task 를 저장하고 꺼낸다. 구현은 Spring Data 가 만든다.
 *
 * `JpaRepository` 가 아니라 `Repository` 를 잇고 **쓰는 메서드만** 선언한다 —
 * 선언하지 않은 `deleteAll` 같은 것은 부를 길이 없다.
 * CRUD 와 이름·파라미터가 같은 메서드는 Spring Data 의 기본 구현으로 가고, 나머지는 이름에서 쿼리를 만든다.
 */
interface GenerationJobTaskRepository : Repository<GenerationJobTask, Long> {

    /** 파라미터가 `Iterable` 이어야 기본 구현의 `saveAll` 과 같은 메서드로 본다. */
    fun saveAll(tasks: Iterable<GenerationJobTask>): List<GenerationJobTask>

    fun findById(id: Long): GenerationJobTask?

    fun findAllByJobIdOrderBySequence(jobId: Long): List<GenerationJobTask>

    /** 오래 기다린 것부터 집도록 id 순으로 [limit] 개를 준다. 개수는 부르는 쪽이 정한다. */
    fun findOldestByStatus(status: TaskStatus, limit: Int): List<GenerationJobTask> =
        findByStatusOrderByIdAsc(status, Limit.of(limit))

    fun findByStatusOrderByIdAsc(status: TaskStatus, limit: Limit): List<GenerationJobTask>
}
