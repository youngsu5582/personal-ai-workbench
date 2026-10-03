package dev.joyson.aiworkbench.upload.domain

/** 업로드 허용 조건. 선언한 형식이 실제 바이트와 일치하는지는 완료 시점에 별도로 확인해야 한다. */
data class UploadSpec(
    val contentType: String,
    val contentLength: Long,
) {
    init {
        require(contentType in ALLOWED_CONTENT_TYPES) { "PNG, JPEG, WebP만 업로드할 수 있다" }
        require(contentLength in 1..MAX_CONTENT_LENGTH) { "파일 크기는 1바이트 이상 20MiB 이하여야 한다" }
    }

    companion object {
        const val MAX_CONTENT_LENGTH = 20L * 1024 * 1024
        val ALLOWED_CONTENT_TYPES = setOf("image/png", "image/jpeg", "image/webp")
    }
}
