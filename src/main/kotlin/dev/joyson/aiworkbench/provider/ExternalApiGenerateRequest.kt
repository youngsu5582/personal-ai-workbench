package dev.joyson.aiworkbench.provider

/**
 * 외부 API 에 보낼 생성 요청.
 *
 * 크기를 **픽셀로** 받는다. 비율·해상도 같은 제품 어휘는 부르는 쪽이 갖고, 여기 도달하기 전에
 * 픽셀로 확정된다. 비율에서 픽셀을 얻는 계산은 결정적이라 무손실이고, 반대는 손실이다
 * (`1920x1080` 을 `16:9 @ 2k` 로 접으면 `2048x1152` 로 돌아온다).
 *
 * `data class` 지만 **통째로 비교하지 않는다.** [images] 의 원소가 `ByteArray` 를 품고 있어
 * 자동 생성된 `equals` 가 이미지를 참조로 본다. 비교가 필요하면 필드 단위로 한다.
 */
data class ExternalApiGenerateRequest(
    val prompt: String,
    val width: Int,
    val height: Int,
    val quality: ImageQuality,

    /**
     * 입력 이미지. **비어 있으면 글에서 만들고(t2i), 있으면 그 이미지를 고친다(i2i).**
     *
     * 종류를 타입으로 가르지 않고 이 목록의 유무로 가른다. 대가는 컴파일러가 분기 누락을
     * 잡아주지 못한다는 것이라, 경로를 고르는 어댑터 쪽에도 같은 규칙을 적어 둔다.
     *
     * 목록인 이유는 API 가 참조 이미지를 여러 장 받기 때문이다. 몇 장까지 받을 수 있는지는
     * API 마다 다르므로 어댑터가 안다 — 여기서는 장수를 제한하지 않는다.
     */
    val images: List<ExternalApiImageInput> = emptyList(),
) {
    init {
        require(prompt.isNotBlank()) { "prompt 는 비어 있을 수 없다" }
        require(width > 0 && height > 0) { "가로·세로는 양수여야 한다" }
    }
}
