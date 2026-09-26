package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.generation.domain.downloadFileNameOf
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.GenerationJobProgress
import dev.joyson.aiworkbench.generation.domain.GenerationJobTask
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
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
 * 파일 하나.
 *
 * 목록에 순서를 뜻하는 값이 없다. 같은 Task 가 낸 파일들은 **줄 세울 것이 아니라 이름 붙일 것**이고,
 * 지금은 한 장뿐이다. 변형(썸네일 등)이 생기면 번호가 아니라 종류로 구분한다.
 */
data class GeneratedFileView(
    val uuid: UUID,
    val metadata: FileMetadata,
    /**
     * 보관소에서 **바로** 받아갈 수 있는 주소. 서명할 수 없는 보관소면 `null`.
     *
     * 우리 경로(`/api/files/{uuid}`)는 web 계층이 만들지만 이 값은 여기서 만든다.
     * **우리 라우팅이 아니라 보관소가 정한 주소**이기 때문이다 — web 은 저 호스트를 알지 못하고,
     * 만들려면 보관소 키가 필요한데 그건 이 계층의 사실이다.
     *
     * 수명이 짧다. 받는 쪽은 이것으로 **그리고**, 오래 살아야 하는 링크(저장·북마크)에는
     * web 이 만드는 우리 경로를 쓴다.
     */
    val previewUrl: String?,
)

/**
 * Entity → 공개 View 매핑.
 *
 * 리포지토리에 의존하지 않는 **순수 함수**다. 그래서 읽기·쓰기 어느 쪽에서 써도
 * 숨은 쿼리가 따라붙지 않고, 스프링 없이 테스트할 수 있다.
 * 매핑 함수가 조회를 하기 시작하면 호출자가 비용을 볼 수 없게 된다.
 *
 * [presignedUrlIssuer] 를 **인자로** 받는 것도 같은 이유다. 서명은 로컬 계산이라 쿼리가 아니지만,
 * 숨겨 두면 이 함수가 무엇에 의존하는지 호출부에서 안 보인다. 없으면 `null` 을 넘긴다.
 */
internal fun GeneratedFile.toView(presignedUrlIssuer: PresignedUrlIssuer?): GeneratedFileView =
    GeneratedFileView(
        uuid = uuid,
        metadata = metadata,
        previewUrl = presignedUrlIssuer
            ?.issue(storageKey, downloadFileNameOf(uuid, metadata.mimeType))
            ?.toString(),
    )

internal fun GenerationJobTask.toView(
    files: List<GeneratedFile>,
    presignedUrlIssuer: PresignedUrlIssuer?,
): GenerationJobTaskView =
    GenerationJobTaskView(
        sequence = sequence,
        status = status,
        failureReason = failureReason,
        files = files.map { it.toView(presignedUrlIssuer) },
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