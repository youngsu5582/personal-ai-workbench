package dev.joyson.aiworkbench.provider.openai

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * OpenAI 전송 설정. 도메인은 이 값들을 모른다.
 *
 * 이 파일은 클라이언트와 함께 움직인다 — 나중에 별도 모듈로 나갈 때 같이 간다.
 */
@ConfigurationProperties(prefix = "workbench.provider.openai")
data class OpenAIProperties(
    /**
     * 없으면 이 Provider 의 빈이 아예 만들어지지 않는다.
     *
     * nullable 인 이유: `@ConfigurationPropertiesScan` 은 `@ConditionalOnProperty` 와 무관하게
     * 이 클래스를 찾아 바인딩한다. non-null 로 두면 **키를 설정하지 않은 환경에서
     * 애플리케이션 전체가 기동에 실패한다.** OpenAI 를 안 쓰는 것이 정상인 상황이 있으므로
     * 여기서는 비어 있어도 되고, 실제 사용 시점([OpenAIClientConfig])에서 확인한다.
     */
    val apiKey: String? = null,
    val baseUrl: String = "https://api.openai.com",
    /**
     * 읽기 타임아웃.
     *
     * 이 API 는 동기라 이미지를 다 만들 때까지 연결이 열려 있다.
     * 기본값을 크게 잡은 것은 그래서다 — 짧으면 만들어진 이미지를 버리고 타임아웃이 난다.
     */
    val readTimeout: Duration = Duration.ofMinutes(3),
    val connectTimeout: Duration = Duration.ofSeconds(10),
)
