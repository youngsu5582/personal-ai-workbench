package dev.joyson.aiworkbench.upload.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageContentTypeTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    @Test
    fun `PNG·JPEG·WebP 를 앞부분으로 알아본다`() {
        assertEquals("image/png", ImageContentType.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13)))
        assertEquals("image/jpeg", ImageContentType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("image/webp", ImageContentType.detect(ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WEBP")))
    }

    @Test
    fun `RIFF 그릇이어도 WEBP 가 아니면 모른다`() {
        assertNull(ImageContentType.detect(ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WAVE")))
    }

    @Test
    fun `이미지가 아니거나 너무 짧으면 모른다`() {
        // zip 의 시작이다.
        assertNull(ImageContentType.detect(bytes(0x50, 0x4B, 0x03, 0x04)))
        // PNG 서명의 앞 4바이트만 있다.
        assertNull(ImageContentType.detect(bytes(0x89, 0x50, 0x4E, 0x47)))
        assertNull(ImageContentType.detect(ByteArray(0)))
    }
}
