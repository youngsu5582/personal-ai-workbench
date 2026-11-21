package dev.joyson.aiworkbench.upload.domain

import dev.joyson.aiworkbench.storage.Sha256

/** 업로드 허용 조건. 선언한 형식이 실제 바이트와 일치하는지는 완료 시점에 별도로 확인해야 한다. */
data class UploadSpec(
    val contentType: String,
    val contentLength: Long,
    val digest: Sha256,
) {
    init {
        require(contentType in ALLOWED_CONTENT_TYPES) { "PNG, JPEG, WebP 이미지와 WAV, MP3, OGG, FLAC, M4A, AAC 오디오만 업로드할 수 있다" }
        require(contentLength in 1..MAX_CONTENT_LENGTH) { "파일 크기는 1바이트 이상 15MiB 이하여야 한다" }
    }

    companion object {
        /**
         * 형식과 상관없이 하나다. Gemini 가 요청 전체(글 + 모든 파일)를 20MB 로 자르고
         * base64 가 약 33% 늘리므로, 원본이 15MiB 아래여야 글과 함께 들어간다.
         */
        const val MAX_CONTENT_LENGTH = 15L * 1024 * 1024
        val ALLOWED_CONTENT_TYPES = setOf(
            "image/png", "image/jpeg", "image/webp",
            "audio/wav", "audio/mpeg", "audio/ogg", "audio/flac", "audio/mp4", "audio/aac",
        )
    }
}
