package dev.joyson.aiworkbench.upload.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileContentTypeTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    @Test
    fun `PNG·JPEG·WebP 를 앞부분으로 알아본다`() {
        assertEquals("image/png", FileContentType.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13)))
        assertEquals("image/jpeg", FileContentType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("image/webp", FileContentType.detect(ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WEBP")))
    }

    /** RIFF 는 WebP · WAV 가 함께 쓰는 그릇이다. 8바이트 뒤가 가른다. */
    @Test
    fun `RIFF 그릇은 WEBP 와 WAVE 로 갈린다`() {
        assertEquals("audio/wav", FileContentType.detect(ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WAVE")))
        assertEquals("image/webp", FileContentType.detect(ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WEBP")))
        // AVI 는 같은 그릇이지만 우리가 받는 형식이 아니다.
        assertNull(FileContentType.detect(ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("AVI ")))
    }

    @Test
    fun `오디오 여섯을 앞부분으로 알아본다`() {
        assertEquals("audio/mpeg", FileContentType.detect(ascii("ID3") + bytes(4, 0, 0, 0, 0, 0, 0, 0, 0)))
        // 태그 없이 프레임으로 시작하는 MP3 — MPEG-1 Layer III.
        assertEquals("audio/mpeg", FileContentType.detect(bytes(0xFF, 0xFB, 0x90, 0x00)))
        assertEquals("audio/ogg", FileContentType.detect(ascii("OggS") + bytes(0, 2, 0, 0, 0, 0, 0, 0)))
        assertEquals("audio/flac", FileContentType.detect(ascii("fLaC") + bytes(0, 0, 0, 0x22, 0, 0, 0, 0)))
        assertEquals("audio/mp4", FileContentType.detect(bytes(0, 0, 0, 0x20) + ascii("ftyp") + ascii("M4A ")))
        // ADTS(AAC) 는 MP3 와 같은 동기 패턴이고 레이어 비트가 00 이다.
        assertEquals("audio/aac", FileContentType.detect(bytes(0xFF, 0xF1, 0x50, 0x80)))
    }

    /** `ftyp` 는 영상 mp4 도 쓴다. 오디오 브랜드(`M4A `)일 때만 받는다. */
    @Test
    fun `영상 mp4 는 모른다`() {
        assertNull(FileContentType.detect(bytes(0, 0, 0, 0x20) + ascii("ftyp") + ascii("isom")))
        assertNull(FileContentType.detect(bytes(0, 0, 0, 0x20) + ascii("ftyp") + ascii("mp42")))
    }

    @Test
    fun `알아보는 모든 형식이 허용 목록에 있다`() {
        val samples = listOf(
            bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), bytes(0xFF, 0xD8, 0xFF), ascii("RIFF") + bytes(0, 0, 0, 0) + ascii("WEBP"),
            ascii("RIFF") + bytes(0, 0, 0, 0) + ascii("WAVE"), ascii("ID3") + bytes(0, 0, 0, 0, 0, 0, 0, 0, 0), ascii("OggS") + bytes(0, 0, 0, 0, 0, 0, 0, 0),
            ascii("fLaC") + bytes(0, 0, 0, 0, 0, 0, 0, 0), bytes(0, 0, 0, 0) + ascii("ftypM4A "), bytes(0xFF, 0xF1, 0, 0),
        )
        val detected = samples.map { requireNotNull(FileContentType.detect(it)) }.toSet()

        assertEquals(UploadSpec.ALLOWED_CONTENT_TYPES, detected)
        assertTrue(samples.all { it.size <= FileContentType.SIGNATURE_LENGTH })
    }

    @Test
    fun `허용하지 않는 형식이거나 너무 짧으면 모른다`() {
        // zip 의 시작이다.
        assertNull(FileContentType.detect(bytes(0x50, 0x4B, 0x03, 0x04)))
        // PNG 서명의 앞 4바이트만 있다.
        assertNull(FileContentType.detect(bytes(0x89, 0x50, 0x4E, 0x47)))
        assertNull(FileContentType.detect(ByteArray(0)))
        // 동기 패턴은 맞지만 레이어 비트가 예약값(00 도 01 도 아닌 10) 이다 — MP3 Layer II.
        assertNull(FileContentType.detect(bytes(0xFF, 0xFD, 0x90, 0x00)))
    }
}
