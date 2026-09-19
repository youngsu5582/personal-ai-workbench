package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.GenerationJobProgress
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GenerationJobReader(
    private val generationJobRepository: GenerationJobRepository,
    private val generationJobTaskRepository: GenerationJobTaskRepository,
) {

    /**
     * 자기 Job 을 읽는다. 남의 것이면 **없는 것과 같게** 돌려준다.
     *
     * 소유자가 아닐 때 403 을 주면 "그 uuid 는 존재한다" 는 사실이 새어 나간다.
     * 찾지 못한 것을 예외가 아니라 `null` 로 돌려주는 이유는 호출 지점마다 처리가 갈리기 때문이다.
     */
    @Transactional(readOnly = true)
    fun findOwned(uuid: UUID, ownerUserId: Long): GenerationJobView? {
        val job = generationJobRepository.findByUuid(uuid)
            ?.takeIf { it.isOwnedBy(ownerUserId) }
            ?: return null

        // Task 는 Job 당 최대 MAX_TASK_COUNT 개라 그대로 가져와 센다.
        // 집계 쿼리를 따로 두면 쿼리는 하나 줄지만, 나중에 파일 목록을 실을 자리가 없어진다.
        val tasks = generationJobTaskRepository.findAllByJobIdOrderBySequence(requireNotNull(job.id))
        return job.toView(GenerationJobProgress.of(job.taskCount, tasks))
    }
}
