package dev.joyson.aiworkbench.provider

import dev.joyson.aiworkbench.provider.domain.ImageSize
import dev.joyson.aiworkbench.provider.domain.Quality

data class ExternalApiGenerateRequest(
    val prompt: String,
    val size: ImageSize,
    val quality: Quality,
)
