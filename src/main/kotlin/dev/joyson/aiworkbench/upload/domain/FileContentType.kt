package dev.joyson.aiworkbench.upload.domain

/**
 * 앞부분 바이트로 업로드 파일의 형식을 알아본다 — 이미지 셋, 오디오 여섯.
 *
 * 매직넘버만 본다. 그 뒤가 끝까지 온전한 파일인지는 모른다 — 그건 실제로 디코드하는 쪽이 안다.
 * 여기서 전체를 디코드하지 않는 이유는 픽셀 수가 수억인 이미지 하나로 메모리가 터질 수 있어서다.
 *
 * 알아보는 말이 곧 허용 목록이다. 형식을 늘리려면 [UploadSpec.ALLOWED_CONTENT_TYPES] 와 함께 여기에 서명을 더한다.
 */
object FileContentType {

    /** 판별에 필요한 앞부분 길이. WebP · WAV · M4A 가 가장 길다 — 8바이트 뒤의 4바이트를 본다. */
    const val SIGNATURE_LENGTH = 12

    private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val RIFF = ascii("RIFF")
    private val WEBP = ascii("WEBP")
    private val WAVE = ascii("WAVE")
    private val ID3 = ascii("ID3")
    private val OGG = ascii("OggS")
    private val FLAC = ascii("fLaC")
    private val FTYP = ascii("ftyp")

    /** iPhone 음성 메모 · 일반 AAC 오디오의 브랜드. 같은 `ftyp` 그릇이 영상(`mp42`, `isom`)도 담아 브랜드까지 본다. */
    private val M4A = ascii("M4A ")

    /** 알아본 형식. 허용 목록에 없으면 `null`. */
    fun detect(leading: ByteArray): String? = when {
        leading.hasAt(0, PNG) -> "image/png"
        leading.hasAt(0, JPEG) -> "image/jpeg"
        // RIFF 는 WebP · WAV · AVI 가 함께 쓰는 그릇이라 그것만으로는 무엇인지 모른다. 8바이트 뒤가 가른다.
        leading.hasAt(0, RIFF) && leading.hasAt(8, WEBP) -> "image/webp"
        leading.hasAt(0, RIFF) && leading.hasAt(8, WAVE) -> "audio/wav"
        leading.hasAt(0, ID3) -> "audio/mpeg"
        leading.hasAt(0, OGG) -> "audio/ogg"
        leading.hasAt(0, FLAC) -> "audio/flac"
        leading.hasAt(4, FTYP) && leading.hasAt(8, M4A) -> "audio/mp4"
        isAdts(leading) -> "audio/aac"
        isMpegAudioFrame(leading) -> "audio/mpeg"
        else -> null
    }

    /**
     * 프레임 동기 11비트가 모두 1 이고 레이어 비트가 `00` 이면 ADTS(AAC) 다.
     * MP3 와 같은 동기 패턴을 쓰고 레이어 비트로만 갈린다 — 그래서 [isMpegAudioFrame] 보다 먼저 본다.
     */
    private fun isAdts(b: ByteArray): Boolean =
        b.size >= 2 && b[0] == 0xFF.toByte() && (b[1].toInt() and 0xF6) == 0xF0

    /** ID3 태그 없이 프레임으로 바로 시작하는 MP3. 레이어 III(`01`) 이고 버전이 예약값(`01`)이 아닌 것만 본다. */
    private fun isMpegAudioFrame(b: ByteArray): Boolean {
        if (b.size < 2 || b[0] != 0xFF.toByte()) return false
        val second = b[1].toInt() and 0xFF
        val syncOk = (second and 0xE0) == 0xE0
        val layerIii = (second and 0x06) == 0x02
        val versionReserved = (second and 0x18) == 0x08
        return syncOk && layerIii && !versionReserved
    }

    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    private fun ByteArray.hasAt(offset: Int, expected: ByteArray): Boolean =
        size >= offset + expected.size && expected.indices.all { this[offset + it] == expected[it] }
}
