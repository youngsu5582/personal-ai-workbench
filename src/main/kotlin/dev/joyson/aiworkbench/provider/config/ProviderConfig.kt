package dev.joyson.aiworkbench.provider.config

import dev.joyson.aiworkbench.StartupSummary
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

    /**
     * 기동 로그에 실을 한 줄.
     *
     * 목록이 비어 있는 것이 **정상이자 흔한 실수**다 — 키를 안 넣으면 어댑터 빈이 아예 안 만들어진다.
     * 지금은 그 사실이 첫 생성 요청의 404 로만 드러나서, 설정을 빠뜨린 것인지
     * 의도한 것인지 구분하는 데 시간이 걸린다.
     */
    @Bean
    fun providerStartupSummary(registry: ProviderRegistry) = StartupSummary {
        val names = registry.availableNames
        if (names.isEmpty()) "Provider=없음 (키를 설정하지 않으면 정상이다. 생성 요청은 전부 거절된다)"
        else "Provider=$names"
    }
}
