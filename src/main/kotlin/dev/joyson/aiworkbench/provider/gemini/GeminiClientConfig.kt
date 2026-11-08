package dev.joyson.aiworkbench.provider.gemini

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

/**
 * API 키가 설정된 경우에만 Gemini 클라이언트와 어댑터를 만든다.
 *
 * 조건을 거는 방식과 이유는 [dev.joyson.aiworkbench.provider.openai.OpenAIClientConfig] 와 같다 —
 * 키가 없으면 빈이 없고, 빈이 없으면 `ProviderRegistry` 에도 안 나타난다.
 */
@Configuration
@ConditionalOnExpression($$"!'${workbench.provider.gemini.api-key:}'.isBlank()")
@EnableConfigurationProperties(GeminiProperties::class)
class GeminiClientConfig {

    @Bean
    fun geminiTextClient(properties: GeminiProperties): GeminiTextClient = GeminiTextClient(restClientOf(properties))

    @Bean
    fun geminiTextProvider(client: GeminiTextClient): GeminiTextProvider = GeminiTextProvider(client)

    private fun restClientOf(properties: GeminiProperties): RestClient {
        val apiKey = requireNotNull(properties.apiKey) {
            "workbench.provider.gemini.api-key 가 필요하다"
        }

        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(properties.readTimeout)
        }

        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            // 쿼리 문자열(`?key=`)로도 받지만 헤더로 보낸다. 주소는 로그 · 프록시 기록에 남는다.
            .defaultHeader(GeminiTextClient.API_KEY_HEADER, apiKey)
            .requestFactory(requestFactory)
            .build()
    }
}
