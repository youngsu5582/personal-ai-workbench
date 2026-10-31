package dev.joyson.aiworkbench.workspace.api

import dev.joyson.aiworkbench.ownership.OwnerContext
import dev.joyson.aiworkbench.workspace.application.WorkspaceService
import dev.joyson.aiworkbench.workspace.domain.Workspace
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * 서비스 소유자는 요청 본문이 아니라 인증에서 확정된 [OwnerContext] 에서 얻는다.
 * 다른 사용자의 서비스는 존재 여부를 노출하지 않도록 상세 조회와 수정에서 404 로 응답한다.
 */
@RestController
@RequestMapping("/api/services")
class WorkspaceController(private val service: WorkspaceService) {
    @PostMapping
    fun create(owner: OwnerContext, @Valid @RequestBody request: WorkspaceRequest): ResponseEntity<WorkspaceResponse> =
        ResponseEntity.status(201).body(service.create(owner.uuid, request.name).toResponse())

    @GetMapping
    fun list(owner: OwnerContext): List<WorkspaceResponse> = service.list(owner.uuid).map { it.toResponse() }

    @GetMapping("/{uuid}")
    fun find(owner: OwnerContext, @PathVariable uuid: UUID): ResponseEntity<WorkspaceResponse> =
        service.find(owner.uuid, uuid)?.let { ResponseEntity.ok(it.toResponse()) } ?: ResponseEntity.notFound().build()

    @PutMapping("/{uuid}")
    fun rename(owner: OwnerContext, @PathVariable uuid: UUID, @Valid @RequestBody request: WorkspaceRequest): ResponseEntity<WorkspaceResponse> =
        service.rename(owner.uuid, uuid, request.name)?.let { ResponseEntity.ok(it.toResponse()) } ?: ResponseEntity.notFound().build()
}

data class WorkspaceRequest(@field:NotBlank @field:Size(max = 255) val name: String)

data class WorkspaceResponse(val uuid: UUID, val name: String, val isDefault: Boolean, val createdAt: Instant)

private fun Workspace.toResponse() = WorkspaceResponse(uuid, name, isDefault, createdAt)
