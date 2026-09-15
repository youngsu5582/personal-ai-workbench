package dev.joyson.aiworkbench.provider

/**
 * 생성된 이미지 1장.
 *
 * 바이트로 받는 이유: 어떤 API 는 base64 를 주고(OpenAI) 어떤 API 는 URL 을 준다.
 * URL 을 주는 쪽은 그 어댑터가 받아오게 한다. 그래야 호출하는 쪽이
 * 만료 시각·리다이렉트·별도 인증 같은 Provider 별 사정을 몰라도 된다.
 *
 * 대가는 메모리다. 4k 이미지가 여러 장 힙에 올라온다 — 동시 처리량을 올리면 여기가 먼저 막힌다.
 * 그때 스트림이나 스토리지 참조로 바꾼다. 지금은 동시성이 1이라 문제가 아니다.
 *
 * `data class` 가 아닌 이유는 [image] 가 `ByteArray` 라서다.
 * 배열은 내용이 아니라 참조로 비교돼서 자동 생성된 `equals` 가 거짓말을 한다.
 */
class ExternalApiGenerateResult(
    val image: ByteArray,
    val metadata: ImageMetadata,
    /** Provider 가 프롬프트를 고쳐 썼다면 그 내용. 없으면 null. */
    val revisedPrompt: String? = null,
) {
    init {
        // 메타데이터가 바이트와 어긋나면 나중에 DB 의 크기와 보관소의 실제 파일이 달라진다.
        // 둘이 떨어져 저장되는 값이라, 만들 때 한 번 맞춰두지 않으면 맞출 기회가 없다.
        require(metadata.fileSize == image.size) {
            "메타데이터의 파일 크기(${metadata.fileSize})가 실제 바이트 수(${image.size})와 다르다"
        }
    }
}
