package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.application.GeneratedFileView
import dev.joyson.aiworkbench.generation.application.GenerationJobTaskView
import dev.joyson.aiworkbench.generation.application.GenerationJobView
import dev.joyson.aiworkbench.generation.domain.GenerationJobProgress
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import java.time.Instant
import java.util.UUID

/**
 * 조회 응답.
 *
 * application 의 View 를 그대로 내보내지 않는 이유는, 그러면 View 에 필드를 더하는 순간
 * **말없이 API 에 새어 나가기** 때문이다. 여기서 한 번 옮겨 적으면 무엇을 공개하는지가 코드에 남는다.
 *
 * 주소를 만드는 것도 여기서 한다 — 경로는 web 계층의 사실이고, application 은 몰라야 한다.
 */
data class GenerationJobDetailResponse(
    val uuid: UUID,
    val status: JobLifecycle,
    val model: String,
    val option: GenerationOption,
    val createdAt: Instant,
    val progress: GenerationJobProgress,
    val tasks: List<TaskResponse>,
)

data class TaskResponse(
    val sequence: Int,
    val status: TaskStatus,
    val failureReason: String?,
    val files: List<FileResponse>,
)

data class FileResponse(
    val uuid: UUID,
    /**
     * **오래 사는** 주소. 받는 쪽은 이 값을 불투명하게 다룬다.
     *
     * 저장·북마크·스크립트처럼 나중에 눌러도 동작해야 하는 길이다.
     * 보관소가 서명할 수 있으면 이 주소가 302 로 서명 주소에 넘긴다.
     */
    val url: String,
    /**
     * **바로 그릴** 주소. 보관소가 서명할 수 없으면 `null`.
     *
     * `<img src>` 는 `Authorization` 헤더를 실을 수 없어서 [url] 로는 못 그린다.
     * 이 값은 서명이 통행증이라 헤더가 필요 없고, 요청이 우리 앱이 아니라 보관소로 바로 간다.
     *
     * **수명이 짧다.** 캐시하거나 오래 들고 있으면 만료된다 — 그때는 [url] 을 쓴다.
     */
    val previewUrl: String?,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val fileSize: Int,
)

fun GenerationJobView.toResponse(): GenerationJobDetailResponse = GenerationJobDetailResponse(
    uuid = uuid,
    status = status,
    model = model,
    option = option,
    createdAt = createdAt,
    progress = progress,
    tasks = tasks.map { it.toResponse() },
)

private fun GenerationJobTaskView.toResponse(): TaskResponse = TaskResponse(
    sequence = sequence,
    status = status,
    failureReason = failureReason,
    files = files.map { it.toResponse() },
)

private fun GeneratedFileView.toResponse(): FileResponse = FileResponse(
    uuid = uuid,
    url = GeneratedFileController.pathOf(uuid),
    previewUrl = previewUrl,
    width = metadata.width,
    height = metadata.height,
    mimeType = metadata.mimeType,
    fileSize = metadata.fileSize,
)
