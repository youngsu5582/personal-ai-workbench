package dev.joyson.aiworkbench.provider.openai

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

/**
 * API 키가 설정된 경우에만 OpenAI 클라이언트를 만든다.
 *
 * 키가 없으면 빈이 없고, 빈이 없으면 Provider 목록에도 안 나타난다 —
 * "지원 목록" 을 따로 관리하지 않아도 설정과 가용성이 한 줄로 이어진다.
 *
 * `@ConditionalOnProperty` 가 아닌 이유: 그쪽은 **값이 비어 있어도 "설정됨" 으로 본다.**
 * `api-key: "${WORKBENCH_PROVIDER_OPENAI_API_KEY:}"` 처럼 기본값 빈 문자열을 두는 순간
 * 키 없는 환경에서도 빈이 만들어지고, 실패가 기동 시점이 아니라 첫 호출의 401 로 미뤄진다.
 */
@Configuration
@ConditionalOnExpression($$"!'${workbench.provider.openai.api-key:}'.isBlank()")
@EnableConfigurationProperties(OpenAIProperties::class)
class OpenAIClientConfig {

    @Bean
    fun openAIImageClient(properties: OpenAIProperties): OpenAIImageClient {
        val apiKey = requireNotNull(properties.apiKey) {
            "workbench.provider.openai.api-key 가 필요하다"
        }

        // 연결 타임아웃은 HttpClient 가, 읽기 타임아웃은 팩토리가 갖는다.
        // 이 API 는 동기라 읽기 쪽이 길어야 한다 — 이미지를 다 만들 때까지 연결이 열려 있다.
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(properties.readTimeout)
        }

        return OpenAIImageClient(
            RestClient.builder()
                .baseUrl(properties.baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .requestFactory(requestFactory)
                .build(),
        )
    }

    /**
     * 어댑터도 같은 조건 아래 둔다.
     *
     * 키가 없는 환경에서는 이 빈이 없고, 없으면 `List<ExternalApiProvider>` 에도 안 담긴다 —
     * "쓸 수 있는 Provider 목록" 을 따로 관리할 필요가 없어진다.
     */
    @Bean
    fun openAIProvider(client: OpenAIImageClient): OpenAIProvider = OpenAIProvider(client)
}
