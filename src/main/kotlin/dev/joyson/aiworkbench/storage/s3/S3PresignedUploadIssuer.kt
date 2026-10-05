package dev.joyson.aiworkbench.storage.s3

import dev.joyson.aiworkbench.storage.PresignedUploadIssuer
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.time.Duration

/**
 * S3 호환 보관소의 presigned PUT 주소를 만든다.
 *
 * [S3PresignedUrlIssuer] 와 같이 **네트워크를 타지 않는다** — 서명은 로컬 계산이다.
 * 비싼 것은 그 주소로 들어오는 실제 전송인데, 그건 우리를 통과하지 않는다.
 * 앱이 바이트를 받지 않는다는 것이 이 경로를 쓰는 이유 전부다.
 */
class S3PresignedUploadIssuer(
    private val presigner: S3Presigner,
    private val bucket: String,
    private val ttl: Duration,
) : PresignedUploadIssuer {

    override fun issueUpload(key: String, contentType: String, contentLength: Long): URI =
        presigner.presignPutObject { request ->
            request
                .signatureDuration(ttl)
                .putObjectRequest { put ->
                    put.bucket(bucket)
                        .key(key)
                        // 서명에 들어간다. 올리는 쪽은 같은 값을 헤더로 보내야 하고, 다르면 보관소가 거절한다.
                        // 우리가 바이트를 보지 못하는 이 경로에서 형식을 못박을 수 있는 유일한 자리다.
                        .contentType(contentType)
                        .contentLength(contentLength)
                }
        }.url().toURI()
}
