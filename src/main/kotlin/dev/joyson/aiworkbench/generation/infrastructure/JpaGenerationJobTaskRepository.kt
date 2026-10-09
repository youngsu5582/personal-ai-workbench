package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.GenerationJobTaskRepository
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/** [GenerationJobTaskRepository] 의 구현. 실제 클래스는 Spring Data 가 만든다. */
interface JpaGenerationJobTaskRepository : JpaRepository<GenerationJobTask, Long>, GenerationJobTaskRepository {

    /**
     * 포트의 `List` 판과 상속한 `Iterable` 판을 Spring Data 가 같은 메서드로 보지 못해, 이어 주지 않으면
     * 이름으로 쿼리를 만들려다 기동이 실패한다.
     *
     * 타입 인자를 적어야 상속한 제네릭 `saveAll` 로 간다. 빼면 자기 자신을 부른다.
     */
    override fun saveAll(tasks: List<GenerationJobTask>): List<GenerationJobTask> = saveAll<GenerationJobTask>(tasks)

    /** 이름이 `findById` 가 아닌 이유는 [JpaGenerationJobRepository.findByIdOrNull] 과 같다. */
    override fun findByIdOrNull(id: Long): GenerationJobTask? = findById(id).orElse(null)

    /** 포트는 개수만 받는다. 페이지는 Spring Data 의 어휘라 여기서 만든다. */
    override fun findOldestByStatus(status: TaskStatus, limit: Int): List<GenerationJobTask> =
        findByStatusOrderByIdAsc(status, PageRequest.of(0, limit))

    fun findByStatusOrderByIdAsc(status: TaskStatus, pageable: Pageable): List<GenerationJobTask>
}
