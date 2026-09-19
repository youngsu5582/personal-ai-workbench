package dev.joyson.aiworkbench.provider.openai

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class OpenAIClientConfigTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(OpenAIClientConfig::class.java)

    @Test
    fun `키가 없으면 Provider 빈이 없다`() {
        runner.run { context ->
            assertThat(context).doesNotHaveBean(OpenAIProvider::class.java)
        }
    }

    @Test
    fun `키가 빈 문자열이면 Provider 빈이 없다`() {
        runner.withPropertyValues("workbench.provider.openai.api-key=").run { context ->
            assertThat(context).doesNotHaveBean(OpenAIProvider::class.java)
        }
    }

    @Test
    fun `키가 있으면 Provider 빈이 생긴다`() {
        runner.withPropertyValues("workbench.provider.openai.api-key=sk-test").run { context ->
            assertThat(context).hasSingleBean(OpenAIProvider::class.java)
        }
    }
}
