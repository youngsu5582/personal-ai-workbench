package dev.joyson.aiworkbench.auth.api

import dev.joyson.aiworkbench.auth.application.ServiceApiKeyService
import dev.joyson.aiworkbench.auth.domain.ServiceApiKey
import dev.joyson.aiworkbench.ownership.OwnerContext
import jakarta.validation.Valid
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/services/{workspaceUuid}/keys")
class ServiceApiKeyController(private val service: ServiceApiKeyService) {
    @PostMapping
    fun issue(owner: OwnerContext, @PathVariable workspaceUuid: UUID, @Valid @RequestBody request: ServiceApiKeyRequest): ResponseEntity<IssuedServiceApiKeyResponse> {
        val issued = service.issue(owner.uuid, workspaceUuid, request.name, request.expiresAt) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
            .body(IssuedServiceApiKeyResponse(issued.key.toResponse(), issued.secret))
    }

    @GetMapping
    fun list(owner: OwnerContext, @PathVariable workspaceUuid: UUID): ResponseEntity<List<ServiceApiKeyResponse>> =
        service.list(owner.uuid, workspaceUuid)?.let { ResponseEntity.ok(it.map(ServiceApiKey::toResponse)) }
            ?: ResponseEntity.notFound().build()

    @DeleteMapping("/{keyUuid}")
    fun revoke(owner: OwnerContext, @PathVariable workspaceUuid: UUID, @PathVariable keyUuid: UUID): ResponseEntity<Void> =
        if (service.revoke(owner.uuid, workspaceUuid, keyUuid)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
}

data class ServiceApiKeyRequest(
    @field:NotBlank @field:Size(max = 255) val name: String,
    @field:Future val expiresAt: Instant? = null,
)

data class ServiceApiKeyResponse(
    val uuid: UUID,
    val name: String,
    val keyHint: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val revokedAt: Instant?,
)

class IssuedServiceApiKeyResponse(val key: ServiceApiKeyResponse, val secret: String)

private fun ServiceApiKey.toResponse() = ServiceApiKeyResponse(uuid, name, keyHint, createdAt, expiresAt, revokedAt)
