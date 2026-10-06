package dev.joyson.aiworkbench.upload.api

import dev.joyson.aiworkbench.ownership.OwnerContext
import dev.joyson.aiworkbench.upload.application.UploadService
import dev.joyson.aiworkbench.upload.domain.UploadSpec
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/uploads")
class UploadController(
    private val uploadService: UploadService,
) {
    @PostMapping
    fun issue(owner: OwnerContext, @Valid @RequestBody request: UploadRequest): ResponseEntity<*> {
        val spec = request.toSpec()
        val upload = uploadService.issue(owner.uuid, spec)
            ?: return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, "현재 보관소는 직접 업로드를 지원하지 않는다"),
            )

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            UploadResponse(
                uuid = upload.uuid,
                url = upload.url.toString(),
                method = "PUT",
                headers = mapOf(
                    HttpHeaders.CONTENT_TYPE to spec.contentType,
                    HttpHeaders.CONTENT_LENGTH to spec.contentLength.toString(),
                ),
            ),
        )
    }
}

data class UploadRequest(
    @field:NotNull
    @field:Pattern(regexp = "image/(png|jpeg|webp)")
    val contentType: String?,
    @field:NotNull
    @field:Min(1)
    @field:Max(UploadSpec.MAX_CONTENT_LENGTH)
    val contentLength: Long?,
) {
    fun toSpec() = UploadSpec(requireNotNull(contentType), requireNotNull(contentLength))
}

data class UploadResponse(
    val uuid: UUID,
    val url: String,
    val method: String,
    val headers: Map<String, String>,
)
