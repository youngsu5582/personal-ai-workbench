package dev.joyson.aiworkbench.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImageMetadataTest {

    private fun metadata(fileSize: Int) =
        ImageMetadata(width = 1024, height = 1024, mimeType = "image/png", fileSize = fileSize)

    @Test
    fun `메타데이터가 바이트 수와 어긋나면 만들 수 없다`() {
        val image = ByteArray(10)

        // 바이트는 보관소로, 메타데이터는 DB 로 따로 간다.
        // 만들 때 안 맞춰두면 나중에 둘을 맞출 기회가 없다.
        val ex = assertFailsWith<IllegalArgumentException> {
            ExternalApiGenerateResult(image = image, metadata = metadata(fileSize = 99))
        }
        assertEquals(true, ex.message?.contains("99"))
    }

    @Test
    fun `바이트 수가 맞으면 만들어진다`() {
        val image = ByteArray(10)

        val result = ExternalApiGenerateResult(image = image, metadata = metadata(fileSize = 10))

        assertEquals(10, result.metadata.fileSize)
    }

    @Test
    fun `크기와 파일 크기는 양수여야 한다`() {
        assertFailsWith<IllegalArgumentException> { metadata(fileSize = 0) }
        assertFailsWith<IllegalArgumentException> {
            ImageMetadata(width = 0, height = 1024, mimeType = "image/png", fileSize = 1)
        }
    }
}
