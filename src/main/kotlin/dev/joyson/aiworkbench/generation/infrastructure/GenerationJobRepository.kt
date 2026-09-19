package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.GenerationJob
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GenerationJobRepository : JpaRepository<GenerationJob, Long> {

    fun findByUuid(uuid: UUID): GenerationJob?

    /**
     * 남은 Task 가 없으면 Job 을 닫는다. 이미 닫혔거나 남은 Task 가 있으면 아무것도 하지 않는다.
     *
     * 엔티티를 읽어 고치지 않고 **조건부 UPDATE** 한 방으로 하는 이유는 경쟁 때문이다.
     * 마지막 Task 들이 거의 동시에 끝나면 여러 워커가 같은 판정을 동시에 하는데,
     * 읽고-판단하고-쓰는 사이에 다른 워커의 커밋이 끼어들면 둘 다 "아직 남았다" 로 보고
     * 아무도 닫지 않는 상태가 된다.
     *
     * 서브쿼리가 `:jobId` 를 다시 쓰지 않고 바깥의 `j.id` 를 참조하는 이유:
     * 같은 이름 파라미터를 두 번 쓰면 이 쿼리가 **조건을 만족해도 0행을 돌려준다**.
     * 상관 서브쿼리로 두면 파라미터가 한 번만 등장해 그 문제를 피하고, 뜻도 같다.
     *
     * `clearAutomatically` 는 벌크 UPDATE 뒤 영속성 컨텍스트에 남은 옛 Job 을 비운다.
     * 없으면 같은 트랜잭션에서 다시 읽을 때 1차 캐시의 PENDING 이 돌아온다.
     *
     * `flushAutomatically` 는 붙이지 않는다. Hibernate 는 네이티브 쿼리의 대상 테이블을
     * 알 수 없으면 세션 전체를 flush 하므로, 바뀐 Task 상태는 이미 나간 뒤다.
     *
     * @return 실제로 닫은 행 수. 0 이면 다른 워커가 이미 닫았거나 아직 끝나지 않았다는 뜻이다.
     */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE generation_jobs j
            SET status = 'CLOSED', updated_at = current_timestamp
            WHERE j.id = :jobId
              AND j.status <> 'CLOSED'
              AND NOT EXISTS (
                  SELECT 1 FROM generation_job_tasks t
                  WHERE t.job_id = j.id AND t.status NOT IN ('SUCCEEDED', 'FAILED')
              )
        """,
        nativeQuery = true,
    )
    fun closeIfAllTasksFinished(@Param("jobId") jobId: Long): Int
}
