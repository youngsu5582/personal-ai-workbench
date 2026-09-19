package dev.joyson.aiworkbench.generation.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
@EnableScheduling
@EnableConfigurationProperties(GenerationWorkerProperties::class)
class GenerationSchedulingConfig {

    /**
     * Task 처리는 대부분 외부 호출을 기다리는 시간이다 — 스레드가 일하는 게 아니라 붙잡혀 있다.
     * 가상 스레드는 그 대기 동안 캐리어를 놓아주므로 풀 크기를 고민하지 않아도 된다.
     *
     * 동시 처리 수는 풀이 아니라 **한 번에 집는 개수**가 정한다
     * ([GenerationWorkerProperties.batchSize]).
     */
    @Bean(destroyMethod = "close")
    fun generationWorkerExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
}
