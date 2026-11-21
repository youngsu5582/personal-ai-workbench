package dev.joyson.aiworkbench.provider.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 길이는 `data` 청크로 센다. 파일 크기로 세면 뒤에 붙는 청크만큼 길게 나온다.
 */
class WavTest {

    /**
     * Gemini 음성 응답의 앞 44바이트 그대로다 — 24kHz 모노 16bit PCM, `data` 430,080바이트.
     * 그 뒤에 7,656바이트짜리 `C2PA` 청크가 붙고, RIFF 크기(437,780)는 그것까지 센다.
     */
    private val geminiHeader = hex("5249464614ae060057415645666d74201000000001000100c05d000080bb0000020010006461746100900600")

    private fun geminiResponse(): ByteArray =
        geminiHeader + ByteArray(430_080) + chunk("C2PA", ByteArray(7_656))

    @Test
    fun `뒤에 붙은 청크는 길이에 넣지 않는다`() {
        val bytes = geminiResponse()

        assertEquals(437_788, bytes.size)
        // 파일 크기로 세면 (437,788 - 44) ÷ 48,000 = 9.12초다.
        assertEquals(8.96, Wav.durationSecondsOf(bytes)!!, 1e-9)
    }

    /** 청크는 순서가 정해져 있지 않다. `fmt ` 앞에 다른 청크가 있어도 이름으로 찾는다. */
    @Test
    fun `fmt 앞의 청크를 건너뛰고 홀수 크기 청크의 채움 바이트를 센다`() {
        val bytes = riff(chunk("LIST", ByteArray(3)) + byteArrayOf(0) + fmt(sampleRate = 16_000, channels = 2, bits = 16) + chunk("data", ByteArray(64_000)))

        assertEquals(1.0, Wav.durationSecondsOf(bytes)!!, 1e-9)
    }

    @Test
    fun `읽을 수 없으면 null 이다`() {
        val pcm = fmt(sampleRate = 24_000, channels = 1, bits = 16)
        val cases = mapOf(
            "RIFF 가 아니다" to "ID3".toByteArray() + ByteArray(100),
            "WAVE 가 아니다" to "RIFF".toByteArray() + ByteArray(4) + "AVI ".toByteArray() + pcm + chunk("data", ByteArray(10)),
            "PCM 이 아니다" to riff(fmt(sampleRate = 24_000, channels = 1, bits = 16, format = 85) + chunk("data", ByteArray(10))),
            "fmt 가 없다" to riff(chunk("data", ByteArray(10))),
            "data 가 없다" to riff(pcm),
            "data 가 비었다" to riff(pcm + chunk("data", ByteArray(0))),
            "data 가 선언한 만큼 바이트가 없다" to riff(pcm) + "data".toByteArray() + le(1_000) + ByteArray(10),
            "헤더보다 짧다" to "RIFF".toByteArray(),
        )

        cases.forEach { (case, bytes) -> assertNull(Wav.durationSecondsOf(bytes), case) }
    }

    private fun riff(body: ByteArray): ByteArray = "RIFF".toByteArray() + le(4 + body.size) + "WAVE".toByteArray() + body

    private fun fmt(sampleRate: Int, channels: Int, bits: Int, format: Int = 1): ByteArray {
        val blockAlign = channels * bits / 8
        val body = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(format.toShort()).putShort(channels.toShort()).putInt(sampleRate)
            .putInt(sampleRate * blockAlign).putShort(blockAlign.toShort()).putShort(bits.toShort())
            .array()
        return chunk("fmt ", body)
    }

    private fun chunk(id: String, body: ByteArray): ByteArray = id.toByteArray() + le(body.size) + body

    private fun le(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
