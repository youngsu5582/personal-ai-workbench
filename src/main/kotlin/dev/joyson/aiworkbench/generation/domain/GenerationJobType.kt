package dev.joyson.aiworkbench.generation.domain

import com.fasterxml.jackson.annotation.JsonValue

enum class GenerationJobType(@get:JsonValue val value: String) {
    TEXT_TO_IMAGE("text-to-image"),

    /** 있는 이미지를 고친다. 무엇을 고칠지는 옵션의 `sources` 가 말한다. */
    IMAGE_TO_IMAGE("image-to-image"),
}