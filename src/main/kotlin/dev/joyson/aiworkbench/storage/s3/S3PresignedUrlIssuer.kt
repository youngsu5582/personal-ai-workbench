package dev.joyson.aiworkbench.storage.s3

import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * S3 호환 보관소의 presigned GET 주소를 만든다.
 *
 * 네트워크를 타지 않는다 — 서명은 **로컬 계산**이라 보관소에 묻지 않고 주소가 나온다.
 * 그래서 발급 자체는 사실상 공짜고, 비싼 것은 그 주소로 들어오는 실제 전송인데
 * 그건 우리를 통과하지 않는다.
 */
class S3PresignedUrlIssuer(
    private val presigner: S3Presigner,
    private val bucket: String,
    private val ttl: Duration,
) : PresignedUrlIssuer {

    override fun issue(key: String, fileName: String): URI =
        presigner.presignGetObject { request ->
            request
                .signatureDuration(ttl)
                .getObjectRequest { get ->
                    get.bucket(bucket)
                        .key(key)
                        // 키가 내용 주소라 그대로 두면 64자 해시가 파일명이 된다.
                        // 이 값은 서명에 포함되므로 받는 쪽이 고칠 수 없다.
                        .responseContentDisposition(contentDisposition(fileName))
                }
        }.url().toURI()

    /**
     * `inline` 이라 브라우저가 바로 그린다. `<img src>` 로 쓰려면 `attachment` 면 안 된다.
     *
     * **이름을 두 번 적는다.** HTTP 헤더는 기본이 latin-1 이라 UTF-8 을 그대로 넣으면 깨진다
     * (`고양이.png` → `ê³ ìì´.png`). RFC 5987 의 `filename*` 이 인코딩을 명시하는 자리이고,
     * 앞의 `filename` 은 그걸 모르는 오래된 클라이언트를 위한 ASCII 대체다.
     *
     * 지금 부르는 쪽이 주는 이름은 `{uuid}.png` 라 전부 ASCII 다. 그럼에도 제대로 적는 이유는,
     * 포트가 **아무 이름이나 받는다**고 해놨기 때문이다 — 비 ASCII 가 들어오는 날 조용히 깨진다.
     *
     * Spring 의 `ContentDisposition` 을 쓰지 않는 이유는 이 모듈이 웹을 모르기 때문이다 —
     * 보관소가 HTTP 응답 헤더 규격을 아는 것까지가 경계다.
     */
    private fun contentDisposition(fileName: String): String {
        val ascii = fileName
            .filter { it.code in PRINTABLE_ASCII && it != '"' && it != '\\' }
            .ifBlank { "download" }
        val encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20")

        return """inline; filename="$ascii"; filename*=UTF-8''$encoded"""
    }

    private companion object {
        private val PRINTABLE_ASCII = 32..126
    }
}
