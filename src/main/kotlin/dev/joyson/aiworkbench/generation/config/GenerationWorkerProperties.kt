package dev.joyson.aiworkbench.generation.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "workbench.generation.worker")
data class GenerationWorkerProperties(
    /** 테스트처럼 스스로 돌면 곤란한 환경에서 끈다. */
    val enabled: Boolean = true,

    /** 한 번에 집어 드는 Task 수. */
    val batchSize: Int = 1,
)
