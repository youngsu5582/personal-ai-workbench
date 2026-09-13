package dev.joyson.aiworkbench.generation.domain

import com.fasterxml.jackson.annotation.JsonValue

enum class GenerationJobType(@get:JsonValue val value: String) {
    TEXT_TO_IMAGE("text-to-image")
}