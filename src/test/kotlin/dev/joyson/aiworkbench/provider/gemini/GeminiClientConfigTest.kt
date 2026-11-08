package dev.joyson.aiworkbench.provider.gemini

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class GeminiClientConfigTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(GeminiClientConfig::class.java)

    @Test
    fun `키가 없으면 Provider 빈이 없다`() {
        runner.run { context ->
            assertThat(context).doesNotHaveBean(GeminiTextProvider::class.java)
        }
    }

    @Test
    fun `키가 빈 문자열이면 Provider 빈이 없다`() {
        runner.withPropertyValues("workbench.provider.gemini.api-key=").run { context ->
            assertThat(context).doesNotHaveBean(GeminiTextProvider::class.java)
        }
    }

    @Test
    fun `키가 있으면 Provider 빈이 생긴다`() {
        runner.withPropertyValues("workbench.provider.gemini.api-key=gm-test").run { context ->
            assertThat(context).hasSingleBean(GeminiTextProvider::class.java)
        }
    }
}
