package dev.joyson.aiworkbench.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 모르는 필드를 로그에 남길 때의 안전장치를 고정한다.
 *
 * 자르는 일을 [UnknownFields.toString] 이 하는 것이 요점이다. 부르는 쪽이 매번 기억해야 한다면
 * 언젠가 잊고, 그때 나가는 것이 수 MB 짜리 이미지다 — **모르는 필드는 크기도 모른다.**
 */
class UnknownFieldsTest {

    private fun fieldsOf(vararg pairs: Pair<String, Any?>) =
        UnknownFields().apply { pairs.forEach { (name, value) -> put(name, value) } }

    @Test
    fun `짧은 값은 그대로 보여준다`() {
        val fields = fieldsOf("size" to "1024x1024", "quality" to "high")

        assertEquals("{size=1024x1024, quality=high}", fields.toString())
    }

    /** 발견이 목적이라 **이름은 전부** 남긴다. 어떤 필드가 왔는지가 승격 판단의 재료다. */
    @Test
    fun `값이 잘려도 이름은 전부 남는다`() {
        val fields = fieldsOf("작은것" to "ok", "큰것" to "가".repeat(5_000))

        assertEquals(setOf("작은것", "큰것"), fields.names)
        assertTrue(fields.toString().contains("작은것=ok"))
    }

    @Test
    fun `이미지 바이트가 모르는 필드로 와도 로그가 터지지 않는다`() {
        val base64Image = "A".repeat(2_000_000)

        val rendered = fieldsOf("어쩌다_들어온_이미지" to base64Image).toString()

        assertTrue(rendered.length < 200, "로그가 ${rendered.length}자나 된다")
        assertFalse(rendered.contains(base64Image), "값이 통째로 실렸다")
        // 얼마나 큰 것이 왔는지는 드러나야 한다 — 그래야 승격할지 판단할 수 있다.
        assertTrue(rendered.contains("(2000000자)"), "잘린 길이가 없다: $rendered")
    }

    /** 자르는 것은 로그뿐이다. 값을 실제로 쓰려는 쪽은 온전한 것을 받아야 한다. */
    @Test
    fun `원값은 잘리지 않은 채로 꺼낼 수 있다`() {
        val long = "가".repeat(5_000)

        val fields = fieldsOf("큰것" to long)

        assertEquals(long, fields.asMap()["큰것"])
    }

    @Test
    fun `중첩된 값도 길면 잘린다`() {
        val rendered = fieldsOf("바깥" to mapOf("안" to List(1_000) { it })).toString()

        assertTrue(rendered.length < 200, "중첩 구조가 그대로 나갔다")
    }

    @Test
    fun `null 값도 안전하게 적는다`() {
        assertEquals("{없는것=null}", fieldsOf("없는것" to null).toString())
    }

    @Test
    fun `모르는 필드가 없으면 비어 있다`() {
        val fields = UnknownFields()

        assertTrue(fields.isEmpty)
        assertFalse(fields.isNotEmpty)
        assertEquals("{}", fields.toString())
    }
}
