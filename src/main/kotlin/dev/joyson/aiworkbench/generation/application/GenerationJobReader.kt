package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.GenerationJobProgress
import dev.joyson.aiworkbench.generation.infrastructure.GeneratedFileRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GenerationJobReader(
    private val generationJobRepository: GenerationJobRepository,
    private val generationJobTaskRepository: GenerationJobTaskRepository,
    private val generatedFileRepository: GeneratedFileRepository,
    /**
     * 보관소가 주소에 서명할 수 있을 때만 있다. 없으면 목록의 `previewUrl` 이 전부 `null` 이고,
     * 받는 쪽은 web 이 만드는 우리 경로로 폴백한다.
     */
    private val presignedUrlIssuer: PresignedUrlIssuer?,
) {

    /**
     * 자기 Job 을 읽는다. 남의 것이면 **없는 것과 같게** 돌려준다.
     *
     * 소유자가 아닐 때 403 을 주면 "그 uuid 는 존재한다" 는 사실이 새어 나간다.
     * 찾지 못한 것을 예외가 아니라 `null` 로 돌려주는 이유는 호출 지점마다 처리가 갈리기 때문이다.
     */
    @Transactional(readOnly = true)
    fun findOwned(uuid: UUID, ownerUuid: UUID): GenerationJobView? {
        val job = generationJobRepository.findByUuid(uuid)
            ?.takeIf { it.isOwnedBy(ownerUuid) }
            ?: return null

        // Task 는 Job 당 최대 MAX_TASK_COUNT 개라 그대로 가져와 센다.
        // 집계 쿼리를 따로 두면 쿼리는 하나 줄지만, 같은 결과에서 파일 목록을 만들 수 없다.
        val tasks = generationJobTaskRepository.findAllByJobIdOrderBySequence(requireNotNull(job.id))

        // Task 마다 한 번씩 묻지 않는다. Task 가 넷이면 쿼리도 넷이 된다.
        val filesByTaskId = generatedFileRepository
            .findAllByTaskIdIn(tasks.mapNotNull { it.id })
            .groupBy { it.taskId }

        return job.toView(
            progress = GenerationJobProgress.of(job.taskCount, tasks),
            tasks = tasks.map { it.toView(filesByTaskId[it.id].orEmpty(), presignedUrlIssuer) },
        )
    }
}
