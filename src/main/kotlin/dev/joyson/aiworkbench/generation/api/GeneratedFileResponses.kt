package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.application.FileJobView
import dev.joyson.aiworkbench.generation.application.OwnedFileListView
import dev.joyson.aiworkbench.generation.application.OwnedFileView
import dev.joyson.aiworkbench.generation.domain.GenerationJobType
import dev.joyson.aiworkbench.generation.domain.option.AudioGenerateOption
import dev.joyson.aiworkbench.generation.domain.option.DecisionOption
import dev.joyson.aiworkbench.generation.domain.option.ImageOption
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption
import java.time.Instant
import java.util.UUID

data class GeneratedFileListResponse(val items: List<GeneratedFileItemResponse>, val hasNext: Boolean)

/**
 * 결과 파일 하나. [file] 은 작업 상세의 결과 파일과 같은 모양이라, 종류가 늘면 [FileResponse] 에만 더한다.
 */
data class GeneratedFileItemResponse(
    val file: FileResponse,
    /** 결과가 만들어진 시각. 작업을 요청한 시각이 아니다. */
    val createdAt: Instant,
    val job: FileJobResponse,
)

/** 결과를 만든 작업. 더 자세한 것은 [uuid] 로 작업 상세를 묻는다. */
data class FileJobResponse(
    val uuid: UUID,
    val type: GenerationJobType,
    val model: String,
    /** 결과를 만든 요청의 글. 이미지는 프롬프트, TTS 는 읽은 글이다. 글이 없는 종류면 `null`. */
    val prompt: String?,
)

fun OwnedFileListView.toResponse(): GeneratedFileListResponse =
    GeneratedFileListResponse(items = items.map { it.toResponse() }, hasNext = hasNext)

private fun OwnedFileView.toResponse(): GeneratedFileItemResponse =
    GeneratedFileItemResponse(file = file.toResponse(), createdAt = createdAt, job = job.toResponse())

private fun FileJobView.toResponse(): FileJobResponse = FileJobResponse(
    uuid = uuid,
    type = option.type,
    model = model,
    // 종류가 늘면 여기서 컴파일이 멈춘다 — 새 종류의 요청 글이 말없이 빠지지 않게.
    prompt = when (option) {
        is ImageOption -> option.prompt
        is TextGenerateOption -> option.prompt
        is AudioGenerateOption -> option.text
        is DecisionOption -> null
    },
)
