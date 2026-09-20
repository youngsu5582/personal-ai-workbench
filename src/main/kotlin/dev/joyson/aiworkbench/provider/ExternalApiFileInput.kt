package dev.joyson.aiworkbench.provider

import java.net.URI

/** 입력 파일은 직접 보낼 바이트 또는 Provider가 읽을 주소다. 보관소 키는 이 경계를 넘지 않는다. */
sealed interface ExternalApiFileInput {
    val mimeType: String

    /**
     * 받는 쪽이 이것을 파일로 다룰 때 쓸 이름.
     *
     * 쓸지는 어댑터가 정한다. 바이트는 파일 파트로 보낼 때 반드시 쓰고, 주소는 그대로 넘기는
     * 어댑터라면 버린다. 그래도 주소에서 비워 두지 않는 이유는, 주소를 받아 파일로 다시 올리는
     * 어댑터가 이름을 지어낼 근거가 없기 때문이다.
     */
    val filename: String

    /** 배열의 참조 비교를 값 비교로 오인하지 않도록 data class로 만들지 않는다. */
    class Bytes(
        val bytes: ByteArray,
        override val mimeType: String,
        override val filename: String,
    ) : ExternalApiFileInput {
        init {
            require(bytes.isNotEmpty()) { "입력 파일은 비어 있을 수 없다" }
            require(mimeType.isNotBlank()) { "형식을 알 수 없는 파일은 보낼 수 없다" }
            require(filename.isNotBlank()) { "파일명이 없으면 파일로 전송되지 않는다" }
        }
    }

    /** 서명 URL은 읽기 권한이므로 자동 생성된 toString에도 노출하지 않는다. */
    class Url(
        val url: URI,
        override val mimeType: String,
        override val filename: String,
    ) : ExternalApiFileInput {
        init {
            require(url.scheme in setOf("http", "https") && !url.host.isNullOrBlank() && url.userInfo == null) {
                "입력 파일 주소는 사용자 정보 없는 절대 HTTP(S) 주소여야 한다"
            }
            require(mimeType.isNotBlank()) { "형식을 알 수 없는 파일은 보낼 수 없다" }
            require(filename.isNotBlank()) { "주소로 넘기는 파일도 이름이 있어야 한다" }
        }
    }
}
