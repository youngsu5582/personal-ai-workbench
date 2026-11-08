package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.domain.GenerationJobType
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

    /**
     * 작업 종류를 [GenerationJobType] 으로 바꾼 뒤 고른다.
     * 문자열로 고르면 종류가 늘어도 이 분기가 조용히 400 을 주고, enum 으로 고르면 컴파일 에러로 드러난다.
     */
    @GetMapping
    fun list(@RequestParam type: String): ModelsResponse {
        val jobType = GenerationJobType.entries.firstOrNull { it.value == type }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 작업 종류다: $type")
        return ModelsResponse(
            when (jobType) {
                GenerationJobType.IMAGE_GENERATE -> registry.imageModels
                GenerationJobType.TEXT_GENERATE -> registry.textModels
                GenerationJobType.DECISION -> registry.decisionModels
            },
        )
    }
}

data class ModelsResponse(val models: List<ProviderModel>)
