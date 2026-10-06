package dev.joyson.aiworkbench.provider

/**
 * 이 호출에 **실제로 쓰인** 파라미터. 우리가 보낸 것과 다를 수 있다.
 *
 * 다른 경우가 둘이다.
 *
 * - **말없이 바꾼다.** 격자에 안 맞는 크기를 가까운 값으로 깎는 식이다.
 * - **우리가 안 보낸 것에 기본값을 넣는다.** `background` 가 그렇다 — 보내지 않았는데 `opaque` 로 돌아온다.
 *
 * 과금은 **이쪽을 따른다.** 돈은 실제로 만든 것에 붙지 우리가 요청한 것에 붙지 않는다.
 *
 * 답해주지 않는 Provider 도 있어 통째로 null 일 수 있다. 그때는 보낸 값이 우리가 가진 전부다.
 *
 * 품질·배경을 enum 이 아니라 문자열로 두는 이유: 저쪽이 우리 눈금에 없는 값을 답할 수 있다.
 * 모르는 값이 왔다고 기록을 통째로 잃는 것보다 그대로 적는 편이 낫다.
 */
data class AppliedParameters(
    val width: Int,
    val height: Int,
    val quality: String? = null,
    val background: String? = null,
    val outputFormat: String? = null,
) {
    init {
        require(width > 0 && height > 0) { "가로·세로는 양수여야 한다" }
    }
}
