package dev.joyson.aiworkbench.upload.domain

/**
 * 앞부분 바이트로 이미지 형식을 알아본다.
 *
 * 매직넘버만 본다. 그 뒤가 끝까지 온전한 이미지인지는 모른다 — 그건 실제로 디코드하는 쪽이 안다.
 * 여기서 전체를 디코드하지 않는 이유는 픽셀 수가 수억인 이미지 하나로 메모리가 터질 수 있어서다.
 */
object ImageContentType {

    /** 판별에 필요한 앞부분 길이. WebP 가 가장 길다 — `RIFF` + 크기 4바이트 + `WEBP`. */
    const val SIGNATURE_LENGTH = 12

    private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
    private val WEBP = "WEBP".toByteArray(Charsets.US_ASCII)

    /** 알아본 형식. PNG·JPEG·WebP 가 아니면 `null`. */
    fun detect(leading: ByteArray): String? = when {
        leading.hasAt(0, PNG) -> "image/png"
        leading.hasAt(0, JPEG) -> "image/jpeg"
        // RIFF 는 WAV·AVI 도 쓰는 그릇이라 그것만으로는 WebP 가 아니다.
        leading.hasAt(0, RIFF) && leading.hasAt(8, WEBP) -> "image/webp"
        else -> null
    }

    private fun ByteArray.hasAt(offset: Int, expected: ByteArray): Boolean =
        size >= offset + expected.size && expected.indices.all { this[offset + it] == expected[it] }
}
