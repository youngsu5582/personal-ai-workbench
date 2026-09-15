package dev.joyson.aiworkbench.provider.config

import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.ProviderRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ProviderConfig {

    /**
     * `ObjectProvider` 로 받는 이유: 어댑터가 하나도 없는 환경이 정상이기 때문이다.
     * `List<ExternalApiProvider>` 를 그대로 주입받으면 후보가 0개일 때 기동이 실패한다.
     */
    @Bean
    fun providerRegistry(providers: ObjectProvider<ExternalApiProvider>): ProviderRegistry =
        ProviderRegistry(providers.orderedStream().toList())
}
