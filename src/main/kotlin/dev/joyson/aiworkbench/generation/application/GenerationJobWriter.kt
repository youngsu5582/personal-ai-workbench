package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobProgress
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GenerationJobWriter(
    private val generationJobRepository: GenerationJobRepository,
    private val generationJobTaskRepository: GenerationJobTaskRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Job 과 Task 를 만든다. **받아도 되는 요청인지는 여기서 묻지 않는다** —
     * 그 판단은 [GenerationJobSubmitter] 가 하고, 이 클래스는 쓰기와 트랜잭션만 맡는다.
     */
    @Transactional
    fun create(userId: Long, command: GenerationCommand): GenerationJobView {
        val job = generationJobRepository.save(
            GenerationJob(
                ownerUserId = userId,
                option = command.option,
                model = command.model,
                taskCount = command.taskCount
            )
        )
        log.info(
            "job 을 저장했습니다. uuid: {}, type={}, model={}, 생성할 작업 개수={}",
            job.uuid, job.option.type, job.model, command.taskCount,
        )
        // `0..n` 은 끝을 포함해 n+1 개가 된다. Task 하나가 곧 Provider 호출 한 번이라 그 차이가 그대로 과금이 된다.
        val taskList = List(command.taskCount) { sequence ->
            GenerationJobTask(jobId = job.id!!, sequence = sequence)
        }
        generationJobTaskRepository.saveAll(taskList)
        log.info("task 들을 저장했습니다. job uuid={}, task uuidList: {}", job.uuid, taskList.map { it.uuid })
        job.dispatch()
        // 방금 만들었으므로 완료 0 이다.
        return job.toView(progress = GenerationJobProgress(total = job.taskCount))
    }
}