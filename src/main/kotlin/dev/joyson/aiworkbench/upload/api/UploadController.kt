package dev.joyson.aiworkbench.upload.api

import dev.joyson.aiworkbench.ownership.OwnerContext
import dev.joyson.aiworkbench.storage.Sha256
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
                sha256 = upload.sha256,
                uploadRequired = upload.url != null,
                url = upload.url?.toString(),
                method = if (upload.url != null) "PUT" else null,
                headers = if (upload.url == null) emptyMap() else mapOf(
                    HttpHeaders.CONTENT_TYPE to spec.contentType,
                    HttpHeaders.CONTENT_LENGTH to spec.contentLength.toString(),
                    "x-amz-checksum-sha256" to spec.digest.base64(),
                    "x-amz-sdk-checksum-algorithm" to "SHA256",
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
    @field:NotNull
    @field:Pattern(regexp = "[0-9a-f]{64}")
    val sha256: String?,
) {
    fun toSpec() = UploadSpec(requireNotNull(contentType), requireNotNull(contentLength), Sha256.parse(requireNotNull(sha256)))
}

data class UploadResponse(
    val sha256: String,
    val uploadRequired: Boolean,
    val url: String?,
    val method: String?,
    val headers: Map<String, String>,
)
