package dev.joyson.aiworkbench.provider

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 포트의 계약을 고정한다.
 *
 * [ExternalApiGenerateRequest.images] 의 기본값은 편의가 아니라 **계약**이다 —
 * 비어 있다는 것이 "글에서 만든다" 는 뜻이라, 기본값이 흔들리면 t2i 가 조용히 i2i 가 되거나
 * 그 반대가 된다. 어느 쪽이든 과금된 뒤에야 드러난다.
 */
class ExternalApiGenerateRequestTest {

    @Test
    fun `이미지를 주지 않으면 빈 목록이다`() {
        val request = ExternalApiGenerateRequest(
            prompt = "고양이",
            width = 1024,
            height = 1024,
            quality = ImageQuality.AUTO,
        )

        assertTrue(request.images.isEmpty(), "기본값이 비어 있어야 기존 호출부가 t2i 로 남는다")
    }

    @Test
    fun `빈 바이트는 입력 이미지가 될 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            ExternalApiImageInput(bytes = ByteArray(0), mimeType = "image/png", filename = "source.png")
        }
    }

    /** 파일명이 없으면 multipart 파트가 파일로 나가지 않는다. */
    @Test
    fun `파일명 없는 입력 이미지는 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            ExternalApiImageInput(bytes = "원본".toByteArray(), mimeType = "image/png", filename = " ")
        }
    }

    @Test
    fun `형식을 모르는 입력 이미지는 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            ExternalApiImageInput(bytes = "원본".toByteArray(), mimeType = "", filename = "source.png")
        }
    }
}
