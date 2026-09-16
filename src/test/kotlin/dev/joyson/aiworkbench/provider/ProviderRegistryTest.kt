package dev.joyson.aiworkbench.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ProviderRegistryTest {

    private class FakeProvider(
        override val name: String,
        private val models: Set<String>,
    ) : ExternalApiProvider {
        override fun supports(model: String) = model in models
        override fun generate(model: String, request: ExternalApiGenerateRequest) =
            ExternalApiGenerateResponse(result = emptyList())
    }

    private val openai = FakeProvider("openai", setOf("gpt-image-2"))
    private val gemini = FakeProvider("gemini", setOf("gemini-image"))

    @Test
    fun `모델을 다루는 어댑터를 찾아준다`() {
        val registry = ProviderRegistry(listOf(openai, gemini))

        assertSame(openai, registry.find("gpt-image-2"))
        assertSame(gemini, registry.find("gemini-image"))
    }

    @Test
    fun `다루는 곳이 없으면 예외가 아니라 null 이다`() {
        val registry = ProviderRegistry(listOf(openai))

        // 무엇을 할지는 호출하는 쪽의 정책이므로 여기서 결정하지 않는다.
        assertNull(registry.find("gemini-image"))
    }

    @Test
    fun `어댑터가 하나도 없어도 동작한다`() {
        // API 키가 없는 환경이 이 상태다.
        val registry = ProviderRegistry(emptyList())

        assertNull(registry.find("gpt-image-2"))
        assertEquals(emptyList(), registry.availableNames)
    }

    @Test
    fun `쓸 수 있는 어댑터 이름을 알려준다`() {
        val registry = ProviderRegistry(listOf(openai, gemini))

        assertEquals(listOf("openai", "gemini"), registry.availableNames)
    }

}
