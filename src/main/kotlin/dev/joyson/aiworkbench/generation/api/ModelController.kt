package dev.joyson.aiworkbench.generation.api

import com.fasterxml.jackson.annotation.JsonInclude
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
                GenerationJobType.IMAGE_GENERATE -> registry.imageModels.map { it.toResponse() }
                GenerationJobType.TEXT_GENERATE -> registry.textModels.map { it.toResponse() }
                GenerationJobType.DECISION -> registry.decisionModels.map { it.toResponse() }
                // 받는 목소리의 정본은 어댑터다. 받는 쪽이 이름 목록을 따로 들고 있지 않게 모델과 함께 준다.
                GenerationJobType.AUDIO_GENERATE -> registry.audioModels.map { model ->
                    model.toResponse(voices = registry.findAudio(model.id)?.voices(model.id)?.sorted().orEmpty())
                }
            },
        )
    }

    private fun ProviderModel.toResponse(voices: List<String>? = null) = ModelResponse(id, provider, voices)
}

data class ModelsResponse(val models: List<ModelResponse>)

data class ModelResponse(
    val id: String,
    val provider: String,
    /** 읽기 모델이 받는 목소리. 다른 종류의 모델에는 칸째 없다 — 해당이 없는 것이지 모르는 것이 아니다. */
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val voices: List<String>? = null,
)
