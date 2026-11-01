package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.provider.ProviderModel
import dev.joyson.aiworkbench.provider.ProviderRegistry
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/models")
class ModelController(private val registry: ProviderRegistry) {
    @GetMapping
    fun list(@RequestParam type: String): ModelsResponse = ModelsResponse(
        when (type) {
            "image-generate" -> registry.imageModels
            "text-generate" -> registry.textModels
            "decision" -> registry.decisionModels
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 작업 종류다: $type")
        },
    )
}

data class ModelsResponse(val models: List<ProviderModel>)
