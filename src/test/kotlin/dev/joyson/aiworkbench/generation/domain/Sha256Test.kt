package dev.joyson.aiworkbench.generation.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Sha256Test {

    @Test
    fun `알려진 벡터와 일치한다`() {
        // NIST 표준 예시. 구현을 바꿔도 이 값이 바뀌면 안 된다.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.of("abc".toByteArray()).hex,
        )
    }

    @Test
    fun `같은 바이트는 같은 값을 준다`() {
        assertEquals(Sha256.of(byteArrayOf(1, 2, 3)), Sha256.of(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `소문자 hex 64자가 아니면 읽어들이지 않는다`() {
        assertFailsWith<IllegalArgumentException> { Sha256.parse("ABC") }
        assertFailsWith<IllegalArgumentException> { Sha256.parse("z".repeat(64)) }
        // 대문자도 거절한다 — 표기가 갈리면 같은 내용이 두 키가 된다.
        assertFailsWith<IllegalArgumentException> { Sha256.parse("BA7816BF".repeat(8)) }
    }
}
