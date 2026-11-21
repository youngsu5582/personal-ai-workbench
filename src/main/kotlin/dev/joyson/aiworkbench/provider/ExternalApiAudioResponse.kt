package dev.joyson.aiworkbench.provider

/**
 * 만들어진 소리 한 편.
 *
 * 바이트는 받은 그대로다 — 내용 주소로 저장되므로 고치면 공개 식별자가 달라진다.
 *
 * `data class` 가 아닌 이유는 [audio] 가 `ByteArray` 라서다([ExternalApiGenerateResult] 와 같다).
 */
class ExternalApiAudioResponse(
    val audio: ByteArray,

    /** `audio/wav` 처럼. 저장할 때 확장자와 Content-Type 을 정한다. */
    val mimeType: String,

    /** 재생 길이(초). 저쪽이 말해 준 값이 아니라 [audio] 에서 읽은 값이다. */
    val durationSeconds: Double,

    /** Provider 가 이 호출에 대해 말한 사용량·비용. 아무것도 말하지 않는 Provider 는 null 이다. */
    val usage: ProviderUsage? = null,

    /** Provider 가 이 응답에 붙인 식별자. 청구서·지원 문의와 대조할 때 쓴다. 없으면 null. */
    val providerId: String? = null,
) {
    init {
        require(audio.isNotEmpty()) { "소리가 비어 있다" }
        require(durationSeconds > 0) { "재생 길이는 양수여야 한다" }
    }
}
