package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.infrastructure.FileLocation
import dev.joyson.aiworkbench.generation.infrastructure.GeneratedFileFinder
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import org.mockito.Mockito.mock
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 어느 길로 내려보내는지를 확인한다.
 *
 * 스프링을 띄우지 않는다 — 여기서 갈리는 것은 **발급자가 있느냐** 하나뿐이고,
 * 그 분기는 객체 두 개로 재현된다.
 */
class GeneratedFileReaderTest {

    private val fileUuid = UUID.randomUUID()
    private val ownerUuid = UUID.randomUUID()
    private val location = FileLocation(storageKey = "users/o/blobs/ab/cd/abcd.png", mimeType = "image/png")

    /** 읽혔는지를 기억한다. "바이트를 읽지 않는다" 가 이 기능의 핵심이라 그것만 본다. */
    private class RecordingStorage(private val content: ByteArray? = byteArrayOf(1, 2, 3)) : FileStorage {
        var readKeys = mutableListOf<String>()
        override fun put(key: String, content: ByteArray, contentType: String) = error("쓰지 않는다")
        override fun read(key: String): ByteArray? {
            readKeys += key
            return content
        }
    }

    private fun finder(result: FileLocation?) = mock(GeneratedFileFinder::class.java).also {
        org.mockito.Mockito.`when`(it.findOwned(fileUuid, ownerUuid)).thenReturn(result)
    }

    @Test
    fun `발급자가 있으면 주소로 넘기고 바이트를 읽지 않는다`() {
        val storage = RecordingStorage()
        val reader = GeneratedFileReader(
            generatedFileFinder = finder(location),
            fileStorage = storage,
            presignedUrlIssuer = { key, fileName -> URI.create("https://storage.test/$key?name=$fileName") },
        )

        val download = reader.download(fileUuid, ownerUuid)

        val redirect = assertIs<FileDownload.Redirect>(download)
        assertEquals("https://storage.test/${location.storageKey}?name=$fileUuid.png", redirect.url.toString())
        // 이 한 줄이 이 기능의 전부다 — 수 MB 가 앱 힙에 올라오지 않는다.
        assertTrue(storage.readKeys.isEmpty())
    }

    @Test
    fun `발급자가 없으면 우리가 읽어 내보낸다`() {
        val storage = RecordingStorage()
        val reader = GeneratedFileReader(finder(location), storage, presignedUrlIssuer = null)

        val streamed = assertIs<FileDownload.Streamed>(reader.download(fileUuid, ownerUuid))

        assertEquals("image/png", streamed.contentType)
        // 받는 사람이 요청한 주소와 파일명이 같아진다. 보관소 키(해시)와는 무관하다.
        assertEquals("$fileUuid.png", streamed.fileName)
        assertEquals(listOf(location.storageKey), storage.readKeys)
    }

    @Test
    fun `남의 파일이면 발급조차 하지 않는다`() {
        // 발급된 주소는 그 자체가 통행증이라, 인가가 그보다 먼저여야 한다.
        val reader = GeneratedFileReader(
            generatedFileFinder = finder(null),
            fileStorage = RecordingStorage(),
            presignedUrlIssuer = { _, _ -> error("여기까지 오면 안 된다") },
        )

        assertNull(reader.download(fileUuid, ownerUuid))
    }

    @Test
    fun `행은 있는데 보관소에 없으면 없는 것으로 답한다`() {
        val reader = GeneratedFileReader(finder(location), RecordingStorage(content = null), null)

        assertNull(reader.download(fileUuid, ownerUuid))
    }
}
