package dev.joyson.aiworkbench.storage.local

import dev.joyson.aiworkbench.storage.FileStorageException
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun `앞부분만 읽고 전체 크기를 함께 안다`() {
        val content = ByteArray(100) { it.toByte() }
        storage.put("range/large.bin", content, "application/octet-stream")

        val head = assertNotNull(storage.readRange("range/large.bin", 0L until 12))

        assertEquals(100, head.totalSize)
        assertContentEquals(content.copyOf(12), head.bytes)
    }

    @Test
    fun `요청보다 작은 파일은 있는 만큼 준다`() {
        storage.put("range/small.png", png, "image/png")

        val head = assertNotNull(storage.readRange("range/small.png", 0L until 12))

        assertEquals(png.size.toLong(), head.totalSize)
        assertContentEquals(png, head.bytes)
    }

    @Test
    fun `가운데 범위만 읽는다`() {
        val content = ByteArray(100) { it.toByte() }
        storage.put("range/middle.bin", content, "application/octet-stream")

        val part = assertNotNull(storage.readRange("range/middle.bin", 10L..19L))

        assertEquals(100, part.totalSize)
        assertContentEquals(content.copyOfRange(10, 20), part.bytes)
    }

    @Test
    fun `시작이 끝을 넘으면 빈 바이트와 전체 크기를 준다`() {
        storage.put("range/short.png", png, "image/png")

        val part = assertNotNull(storage.readRange("range/short.png", 100L..111L))

        assertEquals(png.size.toLong(), part.totalSize)
        assertEquals(0, part.bytes.size)
    }

    @Test
    fun `없는 키를 범위로 읽으면 null 이다`() {
        assertNull(storage.readRange("없는/키.png", 0L until 12))
    }

    /** 없는 것만 null 이다. 열 수 없는 것을 "없다" 로 숨기면 원인이 사라진다. */
    @Test
    fun `읽을 수 없는 자리를 범위로 읽으면 보관 실패다`() {
        // 키 자리에 디렉토리가 있다. 권한으로 만들면 root 로 도는 환경에서 실패가 재현되지 않는다.
        root.resolve("range/dir.png").createDirectories()

        val ex = assertFailsWith<FileStorageException> { storage.readRange("range/dir.png", 0L until 4) }

        assertTrue(ex.retryable)
    }

    @Test
    fun `지운 키는 더 읽히지 않고, 없는 키를 지워도 실패가 아니다`() {
        storage.put("delete/a.png", png, "image/png")

        storage.delete("delete/a.png")
        storage.delete("delete/a.png")

        assertNull(storage.read("delete/a.png"))
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
