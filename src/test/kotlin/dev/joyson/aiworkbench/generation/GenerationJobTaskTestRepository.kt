package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 테스트가 데이터를 준비하고 확인할 때 쓰는 전체 CRUD.
 *
 * 운영 코드의 [dev.joyson.aiworkbench.generation.domain.GenerationJobTaskRepository] 는 쓰는 메서드만 열어 둔다.
 * 테스트에만 필요한 `findAll`·`count`·`saveAndFlush` 를 거기에 올리지 않으려고 따로 둔다.
 */
interface GenerationJobTaskTestRepository : JpaRepository<GenerationJobTask, Long> {

    fun findAllByJobIdOrderBySequence(jobId: Long): List<GenerationJobTask>
}
