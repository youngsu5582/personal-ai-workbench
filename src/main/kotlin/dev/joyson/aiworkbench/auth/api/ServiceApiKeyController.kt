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

/**
 * 로그인 토큰에서 확인한 소유자만 키를 관리한다.
 * 서비스나 키가 없거나 소유하지 않은 경우에는 존재 여부를 숨기기 위해 404 를 반환한다.
 * 발급 응답은 원문을 포함하므로 캐시하지 않는다.
 */
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

/** 만료 시각을 생략하면 무기한이며, 지정한다면 미래 시각이어야 한다. */
data class ServiceApiKeyRequest(
    @field:NotBlank @field:Size(max = 255) val name: String,
    @field:Future val expiresAt: Instant? = null,
)

/** 목록과 발급 응답에 공통으로 쓰는 메타데이터다. 키 원문과 해시는 포함하지 않는다. */
data class ServiceApiKeyResponse(
    val uuid: UUID,
    val name: String,
    val keyHint: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val revokedAt: Instant?,
)

/** 원문은 발급 응답에서만 제공한다. 로그에 노출하지 않도록 toString 을 생성하지 않는다. */
class IssuedServiceApiKeyResponse(val key: ServiceApiKeyResponse, val secret: String)

private fun ServiceApiKey.toResponse() = ServiceApiKeyResponse(uuid, name, keyHint, createdAt, expiresAt, revokedAt)
