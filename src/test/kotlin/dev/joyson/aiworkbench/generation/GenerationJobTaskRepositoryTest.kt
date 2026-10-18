package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.GenerationJobTaskRepository
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.option.ImageOption
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 선언만 한 메서드가 포트가 정한 모양으로 돌아오는지 고정한다.
 *
 * `findById`·`saveAll` 은 기본 구현과 이름·파라미터가 같아 그리로 가는데, 기본 구현은 `Optional`·`Iterable` 을 돌려준다.
 * 포트는 null·`List` 를 약속하므로 그 변환이 실제로 일어나는지가 이 포트의 성립 조건이다.
 */
class GenerationJobTaskRepositoryTest @Autowired constructor(
    private val repository: GenerationJobTaskRepository,
    private val jobRepository: GenerationJobTestRepository,
) : IntegrationTest() {

    private fun savedJobId(): Long =
        jobRepository.saveAndFlush(
            GenerationJob(
                ownerUserUuid = UUID.randomUUID(),
                option = ImageOption("고양이", ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K)),
                model = "gpt-image-2",
                taskCount = 3,
            ),
        ).id!!

    private fun saveTasks(count: Int): List<GenerationJobTask> {
        val jobId = savedJobId()
        return repository.saveAll(List(count) { GenerationJobTask(jobId = jobId, sequence = it) })
    }

    @Test
    fun `없는 id 는 null 이다`() {
        assertNull(repository.findById(Long.MAX_VALUE))
    }

    @Test
    fun `저장한 Task 를 id 로 다시 찾는다`() {
        val saved = saveTasks(count = 2)

        val found = assertNotNull(repository.findById(saved[1].id!!))
        assertEquals(1, found.sequence)
    }

    @Test
    fun `대기 중인 것을 오래 기다린 순으로 개수만큼 준다`() {
        val saved = saveTasks(count = 3)

        val oldest = repository.findOldestByStatus(TaskStatus.PENDING, limit = 2)

        assertEquals(saved.take(2).map { it.id }, oldest.map { it.id })
    }
}
