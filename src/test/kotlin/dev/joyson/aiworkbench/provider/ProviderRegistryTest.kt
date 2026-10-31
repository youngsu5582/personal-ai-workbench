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

    private class FakeTextProvider(
        override val name: String,
        private val models: Set<String>,
    ) : ExternalApiTextProvider {
        override fun supports(model: String) = model in models
        override fun generateText(model: String, request: ExternalApiTextRequest) =
            ExternalApiTextResponse(text = "", stopReason = StopReason.COMPLETED)
    }

    private class FakeDecisionProvider(
        override val name: String,
        private val models: Set<String>,
    ) : ExternalApiDecisionProvider {
        override fun supports(model: String) = model in models
        override fun decide(model: String, request: ExternalApiDecisionRequest) =
            ExternalApiDecisionResponse(answers = emptyList())
    }

    private val openai = FakeProvider("openai", setOf("gpt-image-2"))
    private val gemini = FakeProvider("gemini", setOf("gemini-image"))
    private val openaiText = FakeTextProvider("openai", setOf("gpt-6-luna"))

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

    /** 같은 이름이 양쪽에 있어도 모델이 어느 쪽 일인지는 찾는 함수가 가른다. */
    @Test
    fun `글을 만드는 모델은 텍스트 어댑터에서만 찾는다`() {
        val registry = ProviderRegistry(listOf(openai), listOf(openaiText))

        assertSame(openaiText, registry.findText("gpt-6-luna"))
        assertNull(registry.find("gpt-6-luna"))
        // 이미지 모델로 글을 요청하면 접수에서 걸러져야 한다.
        assertNull(registry.findText("gpt-image-2"))
    }

    /** 같은 모델 이름이 글과 판정을 둘 다 해도 어느 일인지는 찾는 함수가 가른다. */
    @Test
    fun `판정하는 모델은 판정 어댑터에서만 찾는다`() {
        val openaiDecision = FakeDecisionProvider("openai", setOf("gpt-6-luna"))
        val registry = ProviderRegistry(listOf(openai), listOf(openaiText), listOf(openaiDecision))

        assertSame(openaiDecision, registry.findDecision("gpt-6-luna"))
        assertSame(openaiText, registry.findText("gpt-6-luna"))
        assertNull(registry.findDecision("gpt-image-2"))
        assertEquals(listOf("openai"), registry.availableNames)
    }

    @Test
    fun `이미지와 텍스트를 둘 다 하는 Provider 는 이름이 한 번만 나온다`() {
        val registry = ProviderRegistry(listOf(openai, gemini), listOf(openaiText))

        assertEquals(listOf("openai", "gemini"), registry.availableNames)
    }
}
