package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobProgress
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.generation.domain.TaskStatus
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
    /**
     * 결과물을 **자리째** 보여준다.
     *
     * 파일만 평평하게 담으면 실패한 자리가 목록에서 사라져, 4장 중 어느 것이 실패했는지
     * 받는 쪽이 알 수 없다. 집계([progress])는 "끝났나" 를, 이 목록은 "무엇이 나왔나" 를 답한다.
     */
    val tasks: List<GenerationJobTaskView>,
)

data class GenerationJobTaskView(
    val sequence: Int,
    val status: TaskStatus,
    val failureReason: String?,
    val files: List<GeneratedFileView>,
)

/**
 * 파일 하나. 주소는 여기 없다 — 경로를 만드는 것은 web 계층의 일이다.
 *
 * 목록에 순서를 뜻하는 값이 없다. 같은 Task 가 낸 파일들은 **줄 세울 것이 아니라 이름 붙일 것**이고,
 * 지금은 한 장뿐이다. 변형(썸네일 등)이 생기면 번호가 아니라 종류로 구분한다.
 */
data class GeneratedFileView(
    val uuid: UUID,
    val metadata: FileMetadata,
)

/**
 * Entity → 공개 View 매핑.
 *
 * 리포지토리에 의존하지 않는 **순수 함수**다. 그래서 읽기·쓰기 어느 쪽에서 써도
 * 숨은 쿼리가 따라붙지 않고, 스프링 없이 테스트할 수 있다.
 * 매핑 함수가 조회를 하기 시작하면 호출자가 비용을 볼 수 없게 된다.
 */
internal fun GeneratedFile.toView(): GeneratedFileView = GeneratedFileView(
    uuid = uuid,
    metadata = metadata,
)

internal fun GenerationJobTask.toView(files: List<GeneratedFile>): GenerationJobTaskView =
    GenerationJobTaskView(
        sequence = sequence,
        status = status,
        failureReason = failureReason,
        files = files.map { it.toView() },
    )

internal fun GenerationJob.toView(
    progress: GenerationJobProgress,
    tasks: List<GenerationJobTaskView> = emptyList(),
): GenerationJobView = GenerationJobView(
    uuid = uuid,
    status = status,
    model = model,
    option = option,
    createdAt = createdAt,
    progress = progress,
    tasks = tasks,
)