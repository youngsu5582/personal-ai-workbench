package dev.joyson.aiworkbench.generation

import java.util.UUID
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.generation.application.GenerationCommand
import dev.joyson.aiworkbench.generation.application.GenerationJobWriter
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test
import kotlin.test.assertEquals

class GenerationJobWriterTest @Autowired constructor(
    private val writer: GenerationJobWriter,
    private val jobRepository: GenerationJobRepository,
    private val taskRepository: GenerationJobTaskRepository,
) : IntegrationTest() {

    private fun command(taskCount: Int) = GenerationCommand(
        option = TextToImageOption("고양이", ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K)),
        model = MODEL,
        taskCount = taskCount,
    )

    /**
     * `0..n` 은 끝을 포함해 n+1 개를 만든다.
     * Task 하나가 곧 Provider 호출 한 번이라, 이 실수는 그대로 과금으로 이어진다.
     */
    @Test
    fun `요청한 개수만큼만 Task 를 만든다`() {
        writer.create(ownerUuid = UUID.randomUUID(), command = command(taskCount = 4))

        assertEquals(4, taskRepository.findAll().size)
    }

    @Test
    fun `Task 의 sequence 는 0부터 연속이다`() {
        writer.create(ownerUuid = UUID.randomUUID(), command = command(taskCount = 3))

        assertEquals(listOf(0, 1, 2), taskRepository.findAll().map { it.sequence }.sorted())
    }

    @Test
    fun `생성 직후에는 아무것도 완료되지 않았다`() {
        val view = writer.create(ownerUuid = UUID.randomUUID(), command = command(taskCount = 4))

        assertEquals(4, view.progress.total)
        assertEquals(0, view.progress.succeeded, "방금 만든 Job 이 완료로 보고되면 안 된다")
        assertEquals(4, view.progress.remaining)
    }

    @Test
    fun `Task 를 만든 뒤 Job 은 DISPATCHED 이고 Task 는 PENDING 이다`() {
        writer.create(ownerUuid = UUID.randomUUID(), command = command(taskCount = 2))

        assertEquals(JobLifecycle.DISPATCHED, jobRepository.findAll().single().status)
        assertEquals(setOf(TaskStatus.PENDING), taskRepository.findAll().map { it.status }.toSet())
    }

    @Test
    fun `소유자는 인자로 받은 사용자다`() {
        val owner = UUID.randomUUID()
        writer.create(ownerUuid = owner, command = command(taskCount = 1))

        assertEquals(owner, jobRepository.findAll().single().ownerUserUuid)
    }

    private companion object {
        const val MODEL = "fake-image-1"
    }
}
