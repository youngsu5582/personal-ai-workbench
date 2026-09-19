package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface GeneratedFileRepository : JpaRepository<GeneratedFile, Long> {

    fun findAllByTaskId(taskId: Long): List<GeneratedFile>

    /** Task 하나당 한 번씩 묻지 않으려고 한 번에 가져온다. */
    fun findAllByTaskIdIn(taskIds: Collection<Long>): List<GeneratedFile>

    /**
     * 자기 파일만 찾는다. 남의 것이면 없는 것과 같다.
     *
     * `generated_files` 에는 job 도 owner 도 없고 `task_id` 뿐이라 두 단계를 타고 올라간다.
     * 세 테이블 모두 유니크 인덱스로 1행씩 찾으므로 실제로는 lookup 세 번이다.
     * 연관관계가 아니라 조건으로 잇는 것은 모듈 안에서도 FK 를 `Long` 으로 들고 있기 때문이다.
     */
    @Query(
        """
        SELECT f FROM GeneratedFile f, GenerationJobTask t, GenerationJob j
        WHERE f.uuid = :uuid
          AND t.id = f.taskId
          AND j.id = t.jobId
          AND j.ownerUserId = :ownerUserId
        """,
    )
    fun findOwnedByUuid(
        @Param("uuid") uuid: UUID,
        @Param("ownerUserId") ownerUserId: Long,
    ): GeneratedFile?
}
