package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.Sha256
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StorageKeysTest {

    private val owner = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val png = "abc".toByteArray()

    // sha256("abc")
    private val digest = Sha256.of(png)

    @Test
    fun `소유자로 가르고 내용으로 자리를 정한다`() {
        assertEquals(
            "users/$owner/blobs/ba/78/" +
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad.png",
            StorageKeys.generatedFile(owner, digest, "image/png"),
        )
    }

    @Test
    fun `팬아웃 두 조각은 digest 의 앞 네 글자다`() {
        // 별도 값이 아니라 digest 에서 잘라낸다 — 키가 (소유자, 바이트, 형식) 만의 함수로 남는다.
        val key = StorageKeys.generatedFile(owner, digest, "image/png")

        assertTrue(key.contains("/blobs/${digest.hex.substring(0, 2)}/${digest.hex.substring(2, 4)}/"))
    }

    @Test
    fun `같은 소유자와 같은 바이트면 언제나 같은 키다`() {
        // 중복 제거가 성립하는 근거다. 시각이나 난수가 섞이면 깨진다.
        assertEquals(
            StorageKeys.generatedFile(owner, Sha256.of(png), "image/png"),
            StorageKeys.generatedFile(owner, Sha256.of(png), "image/png"),
        )
    }

    @Test
    fun `소유자가 다르면 같은 바이트라도 자리가 다르다`() {
        assertNotEquals(
            StorageKeys.generatedFile(owner, digest, "image/png"),
            StorageKeys.generatedFile(UUID.randomUUID(), digest, "image/png"),
        )
    }

    @Test
    fun `형식이 다르면 키가 갈린다`() {
        // 버그가 아니라 확장자를 붙이기로 한 대가다. 이 도메인에서는 형식이 바이트에서 나오므로
        // 같은 바이트에 다른 형식이 실릴 경로가 없다.
        assertNotEquals(
            StorageKeys.generatedFile(owner, digest, "image/png"),
            StorageKeys.generatedFile(owner, digest, "image/jpeg"),
        )
    }

    @Test
    fun `모르는 형식은 bin 으로 둔다`() {
        assertTrue(StorageKeys.generatedFile(owner, digest, "이상한형식").endsWith(".bin"))
    }
}
