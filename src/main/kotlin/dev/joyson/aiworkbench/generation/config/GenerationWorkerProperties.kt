package dev.joyson.aiworkbench.generation.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "workbench.generation.worker")
data class GenerationWorkerProperties(
    /** 테스트처럼 스스로 돌면 곤란한 환경에서 끈다. */
    val enabled: Boolean = true,

    /**
     * 한 번에 집어 **동시에** 처리하는 Task 수.
     *
     * Provider 호출이 호출당 수십 초라 순차로 돌면 4장짜리 요청이 그 네 배를 기다린다.
     * 기본값을 `GenerationJob.MAX_TASK_COUNT` 와 맞춘 것은 요청 하나가 한 번에 끝나게 하기 위해서다.
     *
     * 동시에 나가는 외부 호출 수이기도 하다 — 올리면 레이트리밋에 먼저 걸린다.
     */
    val batchSize: Int = 4,
)
