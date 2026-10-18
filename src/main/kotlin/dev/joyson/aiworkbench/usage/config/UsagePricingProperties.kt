package dev.joyson.aiworkbench.usage.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "workbench.usage.pricing")
data class UsagePricingProperties(
    /** 테스트처럼 스스로 돌면 곤란한 환경에서 끈다. */
    val enabled: Boolean = true,

    /**
     * 한 주기에 비용을 붙이는 호출 수.
     *
     * 한 건이 외부 호출 없는 DB 안의 계산이라 짧다. 다 못 하면 남은 것은 다음 주기가 받는다.
     */
    val batchSize: Int = 100,
)
