package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.generation.infrastructure.GeneratedFileRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 큐 테이블의 규칙을 고정한다 — 집어드는 것, 되돌리는 것, 닫는 것.
 *
 * 이 셋이 워커의 안전장치다. 여기가 틀리면 같은 Task 가 두 번 처리되거나(과금 두 번),
 * 실패한 Task 가 영원히 재시도되거나, 끝난 Job 이 안 닫힌다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(TaskStateWriter::class)
class TaskStateWriterTest @Autowired constructor(
    private val writer: TaskStateWriter,
    private val jobRepository: GenerationJobRepository,
    private val taskRepository: GenerationJobTaskRepository,
    private val generatedFileRepository: GeneratedFileRepository,
) {

    private fun newJob(taskCount: Int = 2): Pair<Long, List<Long>> {
        val job = jobRepository.saveAndFlush(
            GenerationJob(
                ownerUserId = 1L,
                option = TextToImageOption("고양이", ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K)),
                model = "gpt-image-2",
                taskCount = taskCount,
            ),
        )
        val tasks = taskRepository.saveAll(
            List(taskCount) { GenerationJobTask(jobId = job.id!!, sequence = it) },
        )
        taskRepository.flush()
        return job.id!! to tasks.mapNotNull { it.id }
    }

    private fun metadata() = FileMetadata(1024, 1024, "image/png", 2048)

    @Test
    fun `집어든 Task 는 RUNNING 이 되고 다시 집히지 않는다`() {
        val (_, taskIds) = newJob(taskCount = 2)

        val first = writer.claim(limit = 1)
        val second = writer.claim(limit = 5)

        assertEquals(1, first.size)
        // 이미 집은 것은 두 번째에 안 나온다 — 나오면 같은 요청이 두 번 과금된다.
        assertTrue(first.first() !in second)
        assertEquals(taskIds.size - 1, second.size)
        assertEquals(TaskStatus.RUNNING, taskRepository.findById(first.first()).get().status)
    }

    @Test
    fun `집어들 때마다 시도 횟수가 오른다`() {
        val (_, taskIds) = newJob(taskCount = 1)

        writer.claim(limit = 1)

        assertEquals(1, taskRepository.findById(taskIds.first()).get().attemptCount)
    }

    @Test
    fun `성공하면 결과가 기록되고 마지막 Task 에서 Job 이 닫힌다`() {
        val (jobId, taskIds) = newJob(taskCount = 2)
        writer.claim(limit = 2)

        writer.succeed(taskIds[0], listOf(StoredFile(UUID.randomUUID(), "k0", metadata())))

        // 아직 남은 Task 가 있으면 닫히지 않는다.
        assertEquals(JobLifecycle.PENDING, jobRepository.findById(jobId).get().status)

        writer.succeed(taskIds[1], listOf(StoredFile(UUID.randomUUID(), "k1", metadata())))

        assertEquals(JobLifecycle.CLOSED, jobRepository.findById(jobId).get().status)
        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(taskIds[0]).get().status)
        assertEquals("k0", generatedFileRepository.findAllByTaskId(taskIds[0]).single().storageKey)
    }

    @Test
    fun `다시 해볼 만한 실패는 큐로 되돌아간다`() {
        val (jobId, taskIds) = newJob(taskCount = 1)
        writer.claim(limit = 1)

        writer.fail(taskIds[0], reason = "429", retryable = true)

        assertEquals(TaskStatus.PENDING, taskRepository.findById(taskIds[0]).get().status)
        // 아직 끝나지 않았으므로 Job 도 안 닫힌다.
        assertEquals(JobLifecycle.PENDING, jobRepository.findById(jobId).get().status)
    }

    @Test
    fun `다시 해도 소용없는 실패는 그대로 닫힌다`() {
        val (jobId, taskIds) = newJob(taskCount = 1)
        writer.claim(limit = 1)

        writer.fail(taskIds[0], reason = "400 잘못된 요청", retryable = false)

        val task = taskRepository.findById(taskIds[0]).get()
        assertEquals(TaskStatus.FAILED, task.status)
        assertEquals("400 잘못된 요청", task.failureReason)
        // 모든 Task 가 종료 상태이므로 Job 은 닫힌다. 성공했다는 뜻이 아니다.
        assertEquals(JobLifecycle.CLOSED, jobRepository.findById(jobId).get().status)
    }

    @Test
    fun `시도 상한을 넘으면 더 되돌리지 않는다`() {
        val (_, taskIds) = newJob(taskCount = 1)

        repeat(GenerationJobTask.MAX_ATTEMPTS) {
            writer.claim(limit = 1)
            writer.fail(taskIds[0], reason = "일시적 오류", retryable = true)
        }

        val task = taskRepository.findById(taskIds[0]).get()
        assertEquals(GenerationJobTask.MAX_ATTEMPTS, task.attemptCount)
        assertEquals(TaskStatus.FAILED, task.status)
    }
}
