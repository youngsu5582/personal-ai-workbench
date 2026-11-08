package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.application.GenerationTemplateService
import dev.joyson.aiworkbench.generation.domain.GenerationJobType
import dev.joyson.aiworkbench.generation.domain.GenerationTemplate
import dev.joyson.aiworkbench.generation.domain.option.FileSource
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.ownership.WorkspaceContext
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

/**
 * 인증된 서비스 범위에서 템플릿을 관리하고 생성 요청을 접수한다.
 * [WorkspaceContext]는 선택된 서비스의 소유권을 확인한 뒤 주입된다.
 */
@RestController
@RequestMapping("/api/templates")
class GenerationTemplateController(private val service: GenerationTemplateService) {
    @PostMapping
    fun create(scope: WorkspaceContext, @Valid @RequestBody request: TemplateRequest): ResponseEntity<TemplateResponse> =
        ResponseEntity.status(201).body(
            service.create(scope.owner.uuid, request.name, requireNotNull(request.option), request.inputImageCount, scope.workspaceUuid, request.model.orNull()).toResponse(),
        )

    @GetMapping
    fun list(scope: WorkspaceContext): List<TemplateResponse> =
        service.list(scope.owner.uuid, scope.workspaceUuid).map { it.toResponse() }

    @GetMapping("/{uuid}")
    fun find(scope: WorkspaceContext, @PathVariable uuid: UUID): ResponseEntity<TemplateResponse> =
        service.find(scope.owner.uuid, uuid, scope.workspaceUuid)?.let { ResponseEntity.ok(it.toResponse()) } ?: ResponseEntity.notFound().build()

    @PutMapping("/{uuid}")
    fun update(scope: WorkspaceContext, @PathVariable uuid: UUID, @Valid @RequestBody request: TemplateRequest): ResponseEntity<TemplateResponse> =
        service.update(scope.owner.uuid, uuid, request.name, requireNotNull(request.option), request.inputImageCount, scope.workspaceUuid, request.model.orNull())
            ?.let { ResponseEntity.ok(it.toResponse()) } ?: ResponseEntity.notFound().build()

    @DeleteMapping("/{uuid}")
    fun delete(scope: WorkspaceContext, @PathVariable uuid: UUID): ResponseEntity<Void> =
        if (service.delete(scope.owner.uuid, uuid, scope.workspaceUuid)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    /** 변수와 입력 이미지로 요청을 완성해 비동기 Job을 접수하고, 조회에 사용할 Job UUID를 202 응답으로 돌려준다. */
    @PostMapping("/{uuid}/jobs")
    fun submit(scope: WorkspaceContext, @PathVariable uuid: UUID, @Valid @RequestBody request: TemplateRunRequest): ResponseEntity<GenerationResponse> =
        service.submit(scope.owner.uuid, uuid, request.variables, request.images, request.model.orNull(), request.taskCount, scope.workspaceUuid)
            ?.let { ResponseEntity.accepted().body(GenerationResponse(it.uuid)) } ?: ResponseEntity.notFound().build()
}

/**
 * 템플릿을 정의한다 — 새로 만들거나 전체를 교체할 때의 입력. 함수로 치면 정의다.
 *
 * [option] 은 `/api/jobs` 의 `option` 과 같고, 글 필드에 `{{name}}` 변수 자리를 둘 수 있다.
 * 받을 이미지 수 · 기본 모델과 함께 실행 명세가 된다. 명세의 규칙을 어기면 서비스가 400 으로 돌려준다.
 */
data class TemplateRequest(
    @field:NotBlank @field:Size(max = 255) val name: String,
    @field:NotNull val option: GenerationOption?,
    /** 실행할 때 받을 이미지 수. */
    val inputImageCount: Int = 0,
    /** 실행 요청이 모델을 주지 않을 때 쓸 모델. 비우면 실행할 때마다 고른다. */
    @field:Size(max = 255) val model: String? = null,
)

/**
 * 템플릿을 실행한다 — 함수로 치면 호출이다. 정의의 변수 자리에 넣을 값과 받기로 한 이미지를 준다.
 * 모델을 비우면 템플릿의 기본 모델을 쓴다.
 */
data class TemplateRunRequest(
    /** 템플릿의 변수 이름에 대응하는 값. 변수 자리가 없으면 빈 맵이다. */
    val variables: Map<String, String> = emptyMap(),
    /** 템플릿의 `inputImageCount` 만큼의 이미지. */
    val images: List<FileSource> = emptyList(),
    val model: String? = null,
    @field:Min(1) @field:Max(4) val taskCount: Int = 1,
)

/** 템플릿 식별자와 소속 서비스, 저장된 옵션, 실행 때 채울 변수 · 이미지 수를 담은 관리 응답. */
data class TemplateResponse(
    val uuid: UUID,
    val serviceUuid: UUID,
    val name: String,
    val type: GenerationJobType,
    val option: GenerationOption,
    /** 실행 요청의 `variables` 에 넣을 키. 글 필드에 처음 나온 순서다. */
    val variables: List<String>,
    val inputImageCount: Int,
    /** 실행 요청이 모델을 주지 않을 때 쓰는 모델. 없으면 `null` 이다. */
    val model: String?,
    val createdAt: Instant,
)

private fun GenerationTemplate.toResponse() =
    TemplateResponse(uuid, workspaceUuid, name, spec.option.type, spec.option, spec.variables.toList(), spec.inputImageCount, spec.model, createdAt)

/** 빈 문자열과 "주지 않음" 을 같게 본다. 콘솔의 선택 상자는 고르지 않으면 빈 값을 보낸다. */
private fun String?.orNull(): String? = this?.takeIf { it.isNotBlank() }
