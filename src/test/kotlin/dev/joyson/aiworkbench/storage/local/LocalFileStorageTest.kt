package dev.joyson.aiworkbench.storage.local

import dev.joyson.aiworkbench.storage.FileStorageException
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFileStorageTest {

    @TempDir
    lateinit var root: Path

    private val storage by lazy { LocalFileStorage(root) }

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    @Test
    fun `중간 디렉토리를 만들어가며 저장하고 그대로 읽는다`() {
        storage.put("jobs/abc/tasks/def/0.png", png, "image/png")

        assertContentEquals(png, storage.read("jobs/abc/tasks/def/0.png"))
        assertTrue(root.resolve("jobs/abc/tasks/def/0.png").exists())
    }

    @Test
    fun `없는 키는 예외가 아니라 null 이다`() {
        assertNull(storage.read("없는/키.png"))
    }

    @Test
    fun `재시도가 같은 키를 덮어쓴다`() {
        storage.put("a/0.png", png, "image/png")
        storage.put("a/0.png", byteArrayOf(1, 2, 3), "image/png")

        // 실패한 시도의 잔해가 남지 않는다.
        assertContentEquals(byteArrayOf(1, 2, 3), storage.read("a/0.png"))
    }

    @Test
    fun `보관소 밖을 가리키는 키는 거부한다`() {
        val ex = kotlin.runCatching { storage.put("../탈출.png", png, "image/png") }
            .exceptionOrNull()

        assertTrue(ex is FileStorageException)
        // 다시 시도해도 같은 키는 계속 밖을 가리킨다.
        assertFalse(ex.retryable)
        assertFalse(root.resolveSibling("탈출.png").exists())
    }
}
