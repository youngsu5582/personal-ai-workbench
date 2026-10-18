package dev.joyson.aiworkbench.usage.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * `@EnableScheduling` 은 generation 쪽 설정에도 있다. 여기서도 켜는 이유는 이 모듈의 폴러가
 * 다른 모듈의 설정에 기대지 않게 하기 위해서다 — 두 번 켜도 스케줄러는 하나다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(UsagePricingProperties::class)
class UsagePricingConfig
