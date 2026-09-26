package dev.joyson.aiworkbench.provider

/**
 * 만들어진 이미지에 대한 사실들.
 *
 * 바이트와 따로 두는 이유는 **둘이 다른 곳으로 가기 때문**이다.
 * 바이트는 보관소로, 이 값들은 DB 행으로 간다 — 목록을 보여주거나 용량을 집계할 때
 * 이미지를 읽어올 이유가 없다.
 *
 * `data class` 인 이유는 여기에 배열이 없어서다. 값 비교가 정직하게 동작한다.
 */
data class ImageMetadata(
    /**
     * 만들어진 픽셀 크기.
     *
     * 사용자가 `ImageSize.ByPixels` 로 정확한 값을 말했는데 Provider 격자에 맞춰 깎였다면
     * 그 사실이 여기로 드러난다. 조용히 다른 크기를 주는 것이 제일 나쁘다.
     *
     * 이미지 바이트를 파싱해 얻은 값이 **아니다.** Provider 가 실제로 쓴 값을 답하면 그것이고,
     * 답하지 않으면 우리가 보낸 값이다 — 뒤쪽은 저쪽이 말없이 다르게 만들면 틀릴 수 있다.
     */
    val width: Int,
    val height: Int,

    /** `image/png` 처럼. 저장할 때 확장자와 Content-Type 을 정하는 데 쓴다. */
    val mimeType: String,

    /**
     * 바이트 수.
     *
     * 1024² 가 약 2MB, 2048×1152 가 약 4MB 다.
     * 용량 집계와 보관소 비용 추정에 쓰고, 목록 응답에도 그대로 내보낼 수 있다.
     */
    val fileSize: Int,
) {
    init {
        require(width > 0 && height > 0) { "가로·세로는 양수여야 한다" }
        require(fileSize > 0) { "파일 크기는 양수여야 한다" }
    }
}
