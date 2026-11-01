package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.application.GenerationTemplateService
import dev.joyson.aiworkbench.generation.domain.GenerationTemplate
import dev.joyson.aiworkbench.ownership.WorkspaceContext
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
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
        ResponseEntity.status(201).body(service.create(scope.owner.uuid, request.name, request.systemPrompt, scope.workspaceUuid).toResponse())

    @GetMapping
    fun list(scope: WorkspaceContext): List<TemplateResponse> =
        service.list(scope.owner.uuid, scope.workspaceUuid).map { it.toResponse() }

    @GetMapping("/{uuid}")
    fun find(scope: WorkspaceContext, @PathVariable uuid: UUID): ResponseEntity<TemplateResponse> =
        service.find(scope.owner.uuid, uuid, scope.workspaceUuid)?.let { ResponseEntity.ok(it.toResponse()) } ?: ResponseEntity.notFound().build()

    @PutMapping("/{uuid}")
    fun update(scope: WorkspaceContext, @PathVariable uuid: UUID, @Valid @RequestBody request: TemplateRequest): ResponseEntity<TemplateResponse> =
        service.update(scope.owner.uuid, uuid, request.name, request.systemPrompt, scope.workspaceUuid)?.let { ResponseEntity.ok(it.toResponse()) } ?: ResponseEntity.notFound().build()

    @DeleteMapping("/{uuid}")
    fun delete(scope: WorkspaceContext, @PathVariable uuid: UUID): ResponseEntity<Void> =
        if (service.delete(scope.owner.uuid, uuid, scope.workspaceUuid)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    /** 변수를 채워 비동기 Job을 접수하고, 조회에 사용할 Job UUID를 202 응답으로 돌려준다. */
    @PostMapping("/{uuid}/jobs")
    fun submit(scope: WorkspaceContext, @PathVariable uuid: UUID, @Valid @RequestBody request: TemplateGenerationRequest): ResponseEntity<GenerationResponse> =
        service.submit(scope.owner.uuid, uuid, request.variables, request.prompt, request.model, request.taskCount, scope.workspaceUuid)
            ?.let { ResponseEntity.accepted().body(GenerationResponse(it.uuid)) } ?: ResponseEntity.notFound().build()
}

/** 템플릿을 새로 만들거나 이름과 시스템 지시문 전체를 교체할 때 사용하는 입력. */
data class TemplateRequest(
    @field:NotBlank @field:Size(max = 255) val name: String,
    @field:NotBlank val systemPrompt: String,
)

/** 저장된 지시문의 변수와 이번 실행에 사용할 사용자 입력·모델·작업 수. */
data class TemplateGenerationRequest(
    /** 템플릿의 변수 이름에 대응하는 값. 변수 자리가 없으면 빈 맵이다. */
    val variables: Map<String, String> = emptyMap(),
    @field:NotBlank val prompt: String,
    @field:NotBlank val model: String,
    @field:Min(1) @field:Max(4) val taskCount: Int = 1,
)

/** 템플릿 식별자와 소속 서비스, 저장된 지시문을 포함한 관리 응답. */
data class TemplateResponse(val uuid: UUID, val serviceUuid: UUID, val name: String, val systemPrompt: String, val createdAt: Instant)

private fun GenerationTemplate.toResponse() = TemplateResponse(uuid, workspaceUuid, name, systemPrompt, createdAt)
