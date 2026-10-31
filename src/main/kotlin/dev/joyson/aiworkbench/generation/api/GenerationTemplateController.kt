package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.application.GenerationTemplateService
import dev.joyson.aiworkbench.generation.domain.GenerationTemplate
import dev.joyson.aiworkbench.ownership.OwnerContext
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/templates")
class GenerationTemplateController(private val service: GenerationTemplateService) {
    @PostMapping
    fun create(owner: OwnerContext, @Valid @RequestBody request: TemplateRequest): ResponseEntity<TemplateResponse> =
        ResponseEntity.status(201).body(service.create(owner.uuid, request.name, request.instructions).toResponse())

    @GetMapping
    fun list(owner: OwnerContext): List<TemplateResponse> = service.list(owner.uuid).map { it.toResponse() }

    @GetMapping("/{uuid}")
    fun find(owner: OwnerContext, @PathVariable uuid: UUID): ResponseEntity<TemplateResponse> =
        service.find(owner.uuid, uuid)?.let { ResponseEntity.ok(it.toResponse()) } ?: ResponseEntity.notFound().build()

    @PutMapping("/{uuid}")
    fun update(owner: OwnerContext, @PathVariable uuid: UUID, @Valid @RequestBody request: TemplateRequest): ResponseEntity<TemplateResponse> =
        service.update(owner.uuid, uuid, request.name, request.instructions)?.let { ResponseEntity.ok(it.toResponse()) } ?: ResponseEntity.notFound().build()

    @DeleteMapping("/{uuid}")
    fun delete(owner: OwnerContext, @PathVariable uuid: UUID): ResponseEntity<Void> =
        if (service.delete(owner.uuid, uuid)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @PostMapping("/{uuid}/jobs")
    fun submit(owner: OwnerContext, @PathVariable uuid: UUID, @Valid @RequestBody request: TemplateGenerationRequest): ResponseEntity<GenerationResponse> =
        service.submit(owner.uuid, uuid, request.variables, request.prompt, request.model, request.taskCount)
            ?.let { ResponseEntity.accepted().body(GenerationResponse(it.uuid)) } ?: ResponseEntity.notFound().build()
}

data class TemplateRequest(
    @field:NotBlank @field:Size(max = 255) val name: String,
    @field:NotBlank val instructions: String,
)

data class TemplateGenerationRequest(
    val variables: Map<String, String> = emptyMap(),
    @field:NotBlank val prompt: String,
    @field:NotBlank val model: String,
    @field:Min(1) @field:Max(4) val taskCount: Int = 1,
)

data class TemplateResponse(val uuid: UUID, val name: String, val instructions: String, val createdAt: Instant)

private fun GenerationTemplate.toResponse() = TemplateResponse(uuid, name, instructions, createdAt)
