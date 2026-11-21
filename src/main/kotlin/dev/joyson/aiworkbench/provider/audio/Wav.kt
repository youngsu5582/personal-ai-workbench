package dev.joyson.aiworkbench.provider.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WAV 바이트에서 읽는 사실.
 *
 * 길이는 **`data` 청크의 크기**로 계산한다. RIFF 헤더의 크기는 `data` 뒤에 붙는 다른 청크
 * (출처 메타데이터 등)까지 포함해, 파일 크기로 나누면 그만큼 길게 나온다.
 * 청크는 순서가 정해져 있지 않아 이름으로 찾는다.
 */
internal object Wav {

    const val MIME_TYPE = "audio/wav"

    /**
     * 재생 길이(초). 읽을 수 없으면 null 이다 — RIFF/WAVE 가 아니거나, PCM 이 아니거나,
     * `fmt ` · `data` 청크가 없거나, `data` 가 선언한 크기만큼 바이트가 없을 때.
     *
     * PCM 만 받는다. 압축 형식은 바이트 수로 길이를 셀 수 없다.
     */
    fun durationSecondsOf(bytes: ByteArray): Double? {
        if (bytes.size < HEADER_SIZE || bytes.ascii(0) != "RIFF" || bytes.ascii(8) != "WAVE") return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        var bytesPerSecond: Long? = null
        var offset = HEADER_SIZE
        while (offset + CHUNK_HEADER_SIZE <= bytes.size) {
            val id = bytes.ascii(offset)
            val size = buffer.getInt(offset + 4).toUInt().toLong()
            val body = offset + CHUNK_HEADER_SIZE
            when (id) {
                "fmt " -> {
                    if (size < FMT_MIN_SIZE || body + FMT_MIN_SIZE > bytes.size) return null
                    if (buffer.getShort(body).toInt() != PCM) return null
                    val sampleRate = buffer.getInt(body + 4).toUInt().toLong()
                    val blockAlign = buffer.getShort(body + 12).toUShort().toLong()
                    bytesPerSecond = sampleRate * blockAlign
                }
                "data" -> {
                    val rate = bytesPerSecond?.takeIf { it > 0 } ?: return null
                    if (size == 0L || body + size > bytes.size) return null
                    return size.toDouble() / rate
                }
            }
            // 크기가 홀수인 청크 뒤에는 짝을 맞추는 한 바이트가 붙는다.
            offset = (body + size + size % 2).let { if (it > Int.MAX_VALUE) return null else it.toInt() }
        }
        return null
    }

    private fun ByteArray.ascii(offset: Int): String = String(this, offset, 4, Charsets.US_ASCII)

    private const val HEADER_SIZE = 12
    private const val CHUNK_HEADER_SIZE = 8
    private const val FMT_MIN_SIZE = 16
    private const val PCM = 1
}
