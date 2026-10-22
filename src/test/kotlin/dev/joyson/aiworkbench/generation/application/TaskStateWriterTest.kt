package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.storage.Sha256
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GeneratedTextRepository
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.ProviderInfo
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.option.ImageOption
import dev.joyson.aiworkbench.generation.GeneratedFileTestRepository
import dev.joyson.aiworkbench.generation.GenerationJobTestRepository
import dev.joyson.aiworkbench.generation.GenerationJobTaskTestRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 큐 테이블의 규칙을 고정한다 — 집어드는 것, 되돌리는 것, 닫는 것.
 *
 * 이 셋이 워커의 안전장치다. 여기가 틀리면 같은 Task 가 두 번 처리되거나(과금 두 번),
 * 실패한 Task 가 영원히 재시도되거나, 끝난 Job 이 안 닫힌다.
 */
class TaskStateWriterTest @Autowired constructor(
    private val writer: TaskStateWriter,
    private val jobRepository: GenerationJobTestRepository,
    private val taskRepository: GenerationJobTaskTestRepository,
    private val generatedFileRepository: GeneratedFileTestRepository,
    private val meterRegistry: MeterRegistry,
    private val generatedTextRepository: GeneratedTextRepository,
) : IntegrationTest() {

    private fun newJob(taskCount: Int = 2): Pair<Long, List<Long>> {
        val job = jobRepository.saveAndFlush(
            GenerationJob(
                ownerUserUuid = UUID.randomUUID(),
                option = ImageOption("고양이", ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K)),
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

        writer.succeed(taskIds[0], listOf(StoredFile(Sha256.of(UUID.randomUUID().toString().toByteArray()).hex, "k0", metadata())))

        // 아직 남은 Task 가 있으면 닫히지 않는다.
        assertEquals(JobLifecycle.PENDING, jobRepository.findById(jobId).get().status)

        writer.succeed(taskIds[1], listOf(StoredFile(Sha256.of(UUID.randomUUID().toString().toByteArray()).hex, "k1", metadata())))

        assertEquals(JobLifecycle.CLOSED, jobRepository.findById(jobId).get().status)
        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(taskIds[0]).get().status)
        assertEquals("k0", generatedFileRepository.findAllByTaskId(taskIds[0]).single().storageKey)
    }

    /** 보관소를 거치지 않아 결과 행과 상태 전이가 한 트랜잭션에 든다. 규칙은 파일로 끝난 성공과 같다. */
    @Test
    fun `글 한 편으로 성공하면 행에 남고 마지막 Task 에서 Job 이 닫힌다`() {
        val (jobId, taskIds) = newJob(taskCount = 1)
        writer.claim(limit = 1)
        val before = attempts("succeeded")

        writer.succeed(taskIds[0], "요약입니다", ProviderInfo(providerId = "resp_1"))

        val text = generatedTextRepository.findAllByTaskIdIn(taskIds).single()
        assertEquals("요약입니다", text.content)
        assertEquals("resp_1", text.providerInfo?.providerId)
        assertEquals(TaskStatus.SUCCEEDED, taskRepository.findById(taskIds[0]).get().status)
        assertEquals(JobLifecycle.CLOSED, jobRepository.findById(jobId).get().status)
        val task = taskRepository.findById(taskIds[0]).get()
        assertFalse(assertNotNull(task.finishedAt).isBefore(assertNotNull(task.startedAt)))
        assertEquals(1, attempts("succeeded") - before)
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

    /** 대기는 접수부터 집힐 때까지, 처리는 집힐 때부터 끝날 때까지다. Task 마다 둘이 나와야 느린 Task 를 가를 수 있다. */
    @Test
    fun `집힐 때 시작 시각을, 끝날 때 끝난 시각을 남긴다`() {
        val (_, taskIds) = newJob(taskCount = 1)

        writer.claim(limit = 1)

        val running = taskRepository.findById(taskIds[0]).get()
        val started = assertNotNull(running.startedAt)
        assertNull(running.finishedAt, "아직 끝나지 않았다")

        writer.succeed(taskIds[0], listOf(StoredFile(Sha256.of(UUID.randomUUID().toString().toByteArray()).hex, "k0", metadata())))

        val finished = assertNotNull(taskRepository.findById(taskIds[0]).get().finishedAt)
        assertFalse(finished.isBefore(started))
    }

    /** 시작 시각은 마지막 시도의 것이다. 첫 시도의 것이 남으면 재시도한 Task 의 처리 시간이 앞선 대기까지 먹는다. */
    @Test
    fun `큐로 되돌아가면 끝난 시각을 지우고, 다시 집히면 시작 시각을 바꾼다`() {
        val (_, taskIds) = newJob(taskCount = 1)
        writer.claim(limit = 1)
        val firstStart = Instant.now().minusSeconds(3_600)
        taskRepository.saveAndFlush(taskRepository.findById(taskIds[0]).get().apply { startedAt = firstStart })

        writer.fail(taskIds[0], reason = "429", retryable = true)
        assertNull(taskRepository.findById(taskIds[0]).get().finishedAt, "다시 큐에 들어간 것은 끝난 것이 아니다")

        writer.claim(limit = 1)
        assertTrue(taskRepository.findById(taskIds[0]).get().startedAt!!.isAfter(firstStart))
    }

    /** 재시도율은 큐로 되돌린 시도의 수다. 닫히는 실패까지 재시도로 세면 재시도가 없어도 수가 오른다. */
    @Test
    fun `시도를 결과별로 센다`() {
        val (_, taskIds) = newJob(taskCount = 4)
        writer.claim(limit = 4)
        // 컨텍스트를 공유하므로 다른 테스트가 센 것이 남아 있다. 늘어난 만큼만 본다.
        val before = listOf("succeeded", "retried", "failed").associateWith(::attempts)

        writer.succeed(taskIds[0], listOf(StoredFile(Sha256.of(UUID.randomUUID().toString().toByteArray()).hex, "k0", metadata())))
        writer.fail(taskIds[1], reason = "429", retryable = true)
        writer.fail(taskIds[2], reason = "503", retryable = true)
        writer.fail(taskIds[3], reason = "400 잘못된 요청", retryable = false)

        // 건수를 다르게 둔다. 같으면 재시도와 실패를 뒤바꿔 세도 통과한다.
        val expected = mapOf("succeeded" to 1L, "retried" to 2L, "failed" to 1L)
        before.forEach { (outcome, count) -> assertEquals(expected.getValue(outcome), attempts(outcome) - count, outcome) }
    }

    /** 시도 길이는 벽시계 두 시각의 차다. 시계가 뒤로 가 음수가 되어도 건수는 빠지면 안 된다 — 재시도율이 그 건수다. */
    @Test
    fun `시계가 뒤로 가도 시도는 센다`() {
        val (_, taskIds) = newJob(taskCount = 1)
        writer.claim(limit = 1)
        // 집힌 시각을 미래로 둬 끝난 시각이 그보다 앞서게 한다 — 그 사이 시계가 뒤로 간 것과 같다.
        taskRepository.saveAndFlush(
            taskRepository.findById(taskIds[0]).get().apply { startedAt = Instant.now().plusSeconds(3_600) },
        )
        val before = attempts("failed")

        writer.fail(taskIds[0], reason = "400 잘못된 요청", retryable = false)

        assertEquals(1, attempts("failed") - before)
    }

    private fun attempts(outcome: String): Long =
        meterRegistry.get("workbench.generation.task.attempt").tag("outcome", outcome).timer().count()
}
