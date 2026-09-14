package dev.joyson.aiworkbench.generation.domain

data class GenerationJobProgress(
    val total: Int,
    val succeeded: Int = 0,
    val progress: Int = 0,
    val failed: Int = 0,
) {
}