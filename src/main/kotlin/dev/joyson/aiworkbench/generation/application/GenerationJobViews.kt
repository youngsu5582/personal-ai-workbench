package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobProgress
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import java.time.Instant
import java.util.*

data class GenerationJobView(
    val uuid: UUID,
    val status: JobLifecycle,
    val model: String,
    val option: GenerationOption,
    val createdAt: Instant,
    val progress: GenerationJobProgress,
)

/**
 * Entity → 공개 View 매핑.
 *
 * 리포지토리에 의존하지 않는 **순수 함수**다. 그래서 읽기·쓰기 어느 쪽에서 써도
 * 숨은 쿼리가 따라붙지 않고, 스프링 없이 테스트할 수 있다.
 * 매핑 함수가 조회를 하기 시작하면 호출자가 비용을 볼 수 없게 된다.
 */
internal fun GenerationJob.toView(progress: GenerationJobProgress): GenerationJobView = GenerationJobView(
    uuid = uuid,
    status = status,
    model = model,
    option = option,
    createdAt = createdAt,
    progress = progress,
)