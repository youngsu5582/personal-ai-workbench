package dev.joyson.aiworkbench.provider

/**
 * 외부 API 에 함께 보낼 입력 이미지 1장.
 *
 * 바이트로 받는 이유는 [ExternalApiGenerateResult] 와 같다 — 보관소가 로컬 디스크든 오브젝트
 * 스토어든 어댑터는 실어 보내기만 하면 되고, 어디서 읽어왔는지는 부르는 쪽의 사정이다.
 *
 * `data class` 가 아닌 이유는 [bytes] 가 `ByteArray` 라서다.
 * 배열은 내용이 아니라 참조로 비교돼서 자동 생성된 `equals` 가 거짓말을 한다.
 */
class ExternalApiImageInput(
    val bytes: ByteArray,

    /**
     * `image/png` 처럼.
     *
     * Spring 의 `MediaType` 을 쓰지 않는다. 이 모듈이 web 에 묶이면 전송 방식을 바꿀 때
     * 포트까지 따라 움직인다 — [ImageMetadata.mimeType] 도 같은 이유로 문자열이다.
     */
    val mimeType: String,

    /**
     * 전송할 때 붙일 파일명.
     *
     * multipart 파트는 파일명이 있어야 파일로 취급된다. 이름이 빠지면 받는 쪽이 일반 필드로 읽고,
     * 그 실패는 "요청이 잘못됐다" 로만 돌아와 원인이 멀다.
     */
    val filename: String,
) {
    init {
        require(bytes.isNotEmpty()) { "입력 이미지는 비어 있을 수 없다" }
        require(mimeType.isNotBlank()) { "형식을 알 수 없는 이미지는 보낼 수 없다" }
        require(filename.isNotBlank()) { "파일명이 없으면 파일로 전송되지 않는다" }
    }
}
