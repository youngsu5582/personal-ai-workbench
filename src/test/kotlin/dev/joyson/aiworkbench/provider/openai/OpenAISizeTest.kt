package dev.joyson.aiworkbench.provider.openai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OpenAISizeTest {

    @Test
    fun `보낸 표기를 그대로 다시 읽는다`() {
        val sent = OpenAISize(2048, 1152)

        // 만드는 쪽과 푸는 쪽이 같은 규칙을 써야 응답의 크기를 요청과 비교할 수 있다.
        assertEquals("2048x1152", sent.notation)
        assertEquals(sent, OpenAISize.parseOrNull(sent.notation))
    }

    @Test
    fun `모양이 다르면 예외가 아니라 null 이다`() {
        val unreadable = listOf(null, "", "auto", "1024", "1024x1024x1", "axb", "0x1024", "-1x1024")

        unreadable.forEach { assertNull(OpenAISize.parseOrNull(it), "\"$it\" 를 읽었다고 답했다") }
    }

    @Test
    fun `가로·세로는 양수여야 한다`() {
        assertFailsWith<IllegalArgumentException> { OpenAISize(0, 1024) }
    }
}
