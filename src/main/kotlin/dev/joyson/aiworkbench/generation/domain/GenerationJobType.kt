package dev.joyson.aiworkbench.generation.domain

import com.fasterxml.jackson.annotation.JsonValue

enum class GenerationJobType(@get:JsonValue val value: String) {
    IMAGE_GENERATE("image-generate"),
}
