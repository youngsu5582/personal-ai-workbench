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
    /** 받는 쪽은 이 값을 **불투명하게** 다룬다. 오브젝트 스토어로 바뀌면 서명된 절대 URL 이 된다. */
    val url: String,
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
    width = metadata.width,
    height = metadata.height,
    mimeType = metadata.mimeType,
    fileSize = metadata.fileSize,
)
