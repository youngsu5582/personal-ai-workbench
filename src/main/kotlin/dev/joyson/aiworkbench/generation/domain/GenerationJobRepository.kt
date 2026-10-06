package dev.joyson.aiworkbench.generation.domain

import java.util.UUID

/**
 * Job 을 저장하고 꺼낸다.
 *
 * 도메인이 이 인터페이스를 갖고 저장 기술이 구현한다 — 의존이 도메인 쪽으로 향한다.
 * 구현은 infrastructure 의 `JpaGenerationJobRepository` 다.
 */
interface GenerationJobRepository {

    fun save(job: GenerationJob): GenerationJob

    fun findByIdOrNull(id: Long): GenerationJob?

    fun findByUuid(uuid: UUID): GenerationJob?

    /**
     * 남은 Task 가 없으면 Job 을 닫는다. 이미 닫혔거나 남은 Task 가 있으면 아무것도 하지 않는다.
     *
     * 마지막 Task 들이 거의 동시에 끝나 여럿이 함께 불러도 **한 번만** 닫힌다.
     *
     * @return 실제로 닫은 행 수. 0 이면 다른 쪽이 이미 닫았거나 아직 끝나지 않았다는 뜻이다.
     */
    fun closeIfAllTasksFinished(jobId: Long): Int
}
