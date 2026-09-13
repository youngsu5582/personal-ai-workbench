package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * option 은 종류마다 모양이 달라 JSON 한 컬럼에 담는다.
 * "저장했다 읽으면 원래 타입으로 돌아오는가" 가 이 매핑의 성립 조건이다.
 */
@ActiveProfiles("test")
@DataJpaTest
class GenerationJobPersistenceTest @Autowired constructor(
    private val em: TestEntityManager,
) {

    private fun job(prompt: String = "고양이", taskCount: Int = 2) =
        GenerationJob(ownerUserId = 1L, option = TextToImageOption(prompt), taskCount = taskCount)

    @Test
    fun `option 이 JSON 으로 저장되고 원래 타입으로 읽힌다`() {
        val saved = em.persistAndFlush(job())
        em.clear()

        val loaded = em.find(GenerationJob::class.java, saved.id!!)!!

        val option = assertIs<TextToImageOption>(loaded.option, "다형성 정보가 보존되지 않았다")
        assertEquals("고양이", option.prompt)
    }

    @Test
    fun `생성 직후에는 PENDING 이고 dispatch 로만 넘어간다`() {
        val job = job()
        assertEquals(JobLifecycle.PENDING, job.status)

        job.dispatch()

        assertEquals(JobLifecycle.DISPATCHED, job.status)
        assertFailsWith<IllegalArgumentException> { job.dispatch() }
    }

    @Test
    fun `빈 prompt 는 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { TextToImageOption(" ") }
    }

    /**
     * 상한이 없으면 요청 한 번이 무제한 Provider 호출이 된다.
     * 최후 방어이므로 여기서 걸리면 요청이 아니라 우리 코드의 버그다.
     */
    @Test
    fun `결과물 개수는 1개 이상 상한 이하여야 한다`() {
        assertFailsWith<IllegalArgumentException> { job(taskCount = 0) }
        assertFailsWith<IllegalArgumentException> { job(taskCount = -1) }
        assertFailsWith<IllegalArgumentException> { job(taskCount = GenerationJob.MAX_TASK_COUNT + 1) }

        job(taskCount = 1)
        job(taskCount = GenerationJob.MAX_TASK_COUNT)
    }

    /**
     * 이미 저장된 행을 읽을 때 init 이 다시 돌면, 상한이 바뀐 뒤 옛 행을 못 읽게 된다.
     * kotlin-jpa 의 noArg 생성자가 initializer 를 호출하지 않는 것에 기대고 있으므로 못박는다.
     */
    @Test
    fun `상한을 넘는 기존 행도 읽을 수 있다`() {
        em.entityManager.createNativeQuery(
            """
            insert into generation_jobs (uuid, owner_user_id, option, task_count, status, created_at, updated_at)
            values (random_uuid(), 1, '{"type":"text-to-image","prompt":"과거 데이터"}' format json, 99,
                    'DISPATCHED', current_timestamp, current_timestamp)
            """,
        ).executeUpdate()
        em.flush(); em.clear()

        val loaded = em.entityManager
            .createQuery("select j from GenerationJob j where j.taskCount = 99", GenerationJob::class.java)
            .singleResult

        assertEquals(99, loaded.taskCount)
    }
}
