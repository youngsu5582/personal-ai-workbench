package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.application.GeneratedFileView
import dev.joyson.aiworkbench.generation.application.GenerationJobTaskView
import dev.joyson.aiworkbench.generation.application.GenerationJobView
import dev.joyson.aiworkbench.generation.application.InputFileView
import dev.joyson.aiworkbench.generation.domain.DecisionAnswer
import dev.joyson.aiworkbench.generation.domain.GenerationJobProgress
import dev.joyson.aiworkbench.generation.domain.GenerationJobType
import dev.joyson.aiworkbench.generation.domain.JobLifecycle
import dev.joyson.aiworkbench.generation.domain.TaskStatus
import dev.joyson.aiworkbench.generation.domain.option.FileSource
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.generation.domain.option.TextFormat
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.JsonNodeFactory
import java.time.Instant
import java.util.UUID

/** 항목은 상세 조회와 같은 모양이다. 받는 쪽이 항목마다 상세를 다시 묻지 않는다. */
data class GenerationJobListResponse(val items: List<GenerationJobDetailResponse>, val hasNext: Boolean)

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
    /** [option] 의 `type` 과 같다. 목록에서 종류만 보고 고를 때 option 을 열지 않게 둔다. */
    val type: GenerationJobType,
    val model: String,
    val option: GenerationOption,
    /** 요청이 참조한 파일. 참조가 없는 종류면 빈 목록이다. */
    val inputs: List<InputFileResponse>,
    val createdAt: Instant,
    /** [GenerationJobProgress.total] 과 같다. 목록을 읽는 쪽이 이 칸을 쓰므로 지우지 않는다. */
    val taskCount: Int,
    val progress: GenerationJobProgress,
    val tasks: List<TaskResponse>,
)

/** 요청이 참조한 파일 하나. [source] 는 `option` 에 적힌 참조와 같은 값이고, 그릴 주소만 여기서 붙는다. */
data class InputFileResponse(
    val source: FileSource,
    /**
     * **오래 사는** 주소. 결과물을 참조했으면 [FileResponse.url] 과 같은 경로다.
     * 올린 파일은 우리가 내려보내는 경로가 없어 `null` 이다.
     */
    val url: String?,
    /** **바로 그릴** 주소. 서명할 수 없거나 찾지 못하면 `null`. 수명이 짧다. */
    val previewUrl: String?,
)

data class TaskResponse(
    val sequence: Int,
    val status: TaskStatus,
    val failureReason: String?,
    /** 이미지 결과물. 다른 종류의 Task 면 빈 목록이다. */
    val files: List<FileResponse>,
    /** 글 결과물. 다른 종류의 Task 면 빈 목록이다. */
    val texts: List<TextResponse>,
    /** 판정 결과물. 다른 종류의 Task 면 빈 목록이다. */
    val decisions: List<DecisionResponse>,
    /**
     * **마지막 시도가** 집힌 시각. 아직 집히지 않았으면 `null` 이다.
     *
     * Job 의 `createdAt` 부터 이 시각까지가 대기다. 재시도한 Task 는 앞선 시도에 쓴 시간도 그 안에 든다.
     * 이 시각을 남기기 전에 끝난 Task 도 `null` 이라, `null` 만으로 "아직 안 집혔다" 고 읽지 않는다 — [status] 를 함께 본다.
     */
    val startedAt: Instant?,
    /** 끝난 시각. 성공이든 실패든 끝나야 찬다. 다시 큐로 돌아간 Task 는 `null` 로 돌아간다. */
    val finishedAt: Instant?,
)

data class DecisionResponse(
    /**
     * 답은 `name` 으로 질문을 가리킨다. 순서는 Provider 가 준 그대로라 질문의 순서와 같다고 기대지 않는다.
     * 각 답의 `type` 이 질문의 종류(`predicate` · `choice` · `score`)이거나 `refusal` 이다.
     */
    val answers: List<DecisionAnswer>,
)

data class TextResponse(
    /**
     * **요청한 형식이 값의 모양을 정한다.** `text` 면 문자열, `json-schema` 면 그 스키마를 따르는 객체다.
     *
     * 구조화된 답을 문자열로 내보내지 않는다. 받는 쪽이 매번 다시 파싱해야 한다면 스키마를 준 의미가 반쯤 사라진다.
     * 키 순서는 모델이 낸 그대로다 — 저장은 원문이고, 읽을 때 순서를 지키는 파서로 읽는다.
     */
    val content: JsonNode,
)

data class FileResponse(
    val sha256: String,
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

fun GenerationJobView.toResponse(): GenerationJobDetailResponse {
    // 글의 모양은 Task 마다가 아니라 요청 하나에 정해진다.
    val textFormat = (option as? TextGenerateOption)?.output ?: TextFormat.Text
    return GenerationJobDetailResponse(
        uuid = uuid,
        status = status,
        type = option.type,
        model = model,
        option = option,
        inputs = inputs.map { it.toResponse() },
        createdAt = createdAt,
        taskCount = taskCount,
        progress = progress,
        tasks = tasks.map { it.toResponse(textFormat) },
    )
}

private fun InputFileView.toResponse(): InputFileResponse = InputFileResponse(
    source = source,
    url = when (source) {
        is FileSource.Generated -> GeneratedFileController.pathOf(source.sha256)
        is FileSource.Uploaded -> null
    },
    previewUrl = previewUrl,
)

private fun GenerationJobTaskView.toResponse(textFormat: TextFormat): TaskResponse = TaskResponse(
    sequence = sequence,
    status = status,
    failureReason = failureReason,
    files = files.map { it.toResponse() },
    texts = texts.map { TextResponse(contentOf(it.content, textFormat)) },
    decisions = decisions.map { DecisionResponse(it.answers) },
    startedAt = startedAt,
    finishedAt = finishedAt,
)

/** 성공으로 기록된 글은 워커가 형식을 확인한 뒤라 여기서 파싱이 실패하지 않는다. */
private fun contentOf(content: String, format: TextFormat): JsonNode = when (format) {
    TextFormat.Text -> JsonNodeFactory.instance.stringNode(content)
    is TextFormat.JsonSchema -> JsonMapper.shared().readTree(content)
}

private fun GeneratedFileView.toResponse(): FileResponse = FileResponse(
    sha256 = sha256,
    url = GeneratedFileController.pathOf(sha256),
    previewUrl = previewUrl,
    width = metadata.width,
    height = metadata.height,
    mimeType = metadata.mimeType,
    fileSize = metadata.fileSize,
)
