package dev.joyson.aiworkbench.provider.gemini

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Gemini API 전송 설정. 도메인은 이 값들을 모른다.
 *
 * 키가 nullable 인 이유와 읽기 타임아웃을 길게 잡은 이유는 OpenAI 쪽과 같다
 * ([dev.joyson.aiworkbench.provider.openai.OpenAIProperties]).
 */
@ConfigurationProperties(prefix = "workbench.provider.gemini")
data class GeminiProperties(
    /** 없으면 이 Provider 의 빈이 아예 만들어지지 않는다. */
    val apiKey: String? = null,
    val baseUrl: String = "https://generativelanguage.googleapis.com",
    /** 동기 호출이라 다 생각하고 답할 때까지 연결이 열려 있다. */
    val readTimeout: Duration = Duration.ofMinutes(3),
    val connectTimeout: Duration = Duration.ofSeconds(10),
)
