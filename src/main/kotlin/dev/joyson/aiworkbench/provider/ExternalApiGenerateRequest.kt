package dev.joyson.aiworkbench.provider

/**
 * 외부 API 에 보낼 생성 요청.
 *
 * 크기를 **픽셀로** 받는다. 비율·해상도 같은 제품 어휘는 부르는 쪽이 갖고, 여기 도달하기 전에
 * 픽셀로 확정된다. 비율에서 픽셀을 얻는 계산은 결정적이라 무손실이고, 반대는 손실이다
 * (`1920x1080` 을 `16:9 @ 2k` 로 접으면 `2048x1152` 로 돌아온다).
 */
data class ExternalApiGenerateRequest(
    val prompt: String,
    val width: Int,
    val height: Int,
    val quality: ImageQuality,
) {
    init {
        require(prompt.isNotBlank()) { "prompt 는 비어 있을 수 없다" }
        require(width > 0 && height > 0) { "가로·세로는 양수여야 한다" }
    }
}
