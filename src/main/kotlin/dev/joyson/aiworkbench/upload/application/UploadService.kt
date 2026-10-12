package dev.joyson.aiworkbench.upload.application

import dev.joyson.aiworkbench.storage.PresignedUploadIssuer
import dev.joyson.aiworkbench.upload.domain.UploadSpec
import dev.joyson.aiworkbench.upload.domain.UploadedFile
import dev.joyson.aiworkbench.upload.domain.UploadedFileMetadata
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.util.UUID

@Service
class UploadService(
    private val presignedUploadIssuer: PresignedUploadIssuer?,
    private val uploadedFileRepository: UploadedFileRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 직접 업로드를 지원하지 않는 보관소면 null 이다. */
    fun issue(ownerUuid: UUID, spec: UploadSpec): UploadUrl? {
        val issuer = presignedUploadIssuer ?: return null
        val uuid = UUID.randomUUID()
        // 내용을 검증하기 전에는 생성 결과의 blobs 경로에 쓰기 권한을 주지 않는다.
        val key = "users/$ownerUuid/uploads/$uuid"
        val url = issuer.issueUpload(key, spec.contentType, spec.contentLength)
        // 서명한 뒤에 남긴다. 먼저 남기면 서명이 실패했을 때 아무도 올릴 수 없는 행이 생긴다.
        uploadedFileRepository.save(
            UploadedFile(
                uuid = uuid,
                ownerUserUuid = ownerUuid,
                storageKey = key,
                metadata = UploadedFileMetadata(mimeType = spec.contentType, fileSize = spec.contentLength),
            ),
        )
        val upload = UploadUrl(uuid, url)
        // 서명 URL 은 쓰기 권한이므로 로그에 남기지 않는다. 발급 식별자와 허용 조건만 기록한다.
        log.info(
            "업로드 주소를 발급했다. uuid={} ownerUuid={} type={} bytes={}",
            uuid, ownerUuid, spec.contentType, spec.contentLength,
        )
        return upload
    }
}

data class UploadUrl(
    val uuid: UUID,
    val url: URI,
)
