package dev.joyson.aiworkbench.upload.application

import dev.joyson.aiworkbench.storage.BlobKey
import dev.joyson.aiworkbench.storage.PresignedUploadIssuer
import dev.joyson.aiworkbench.upload.domain.UploadSpec
import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFile
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.util.UUID

@Service
class UploadService(
    private val presignedUploadIssuer: PresignedUploadIssuer?,
    private val uploadedFileRepository: UploadedFileRepository,
    private val uploadedFileWriter: UploadedFileWriter,
    private val uploadedFiles: DefaultUploadedFiles,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** READY 는 전송 없이 재사용한다. PENDING 중복은 올라온 객체가 있으면 먼저 판정한다. */
    fun issue(ownerUuid: UUID, spec: UploadSpec): UploadUrl? {
        val issuer = presignedUploadIssuer ?: return null
        val key = BlobKey.uploaded(ownerUuid, spec.digest, spec.contentType).value
        val existing = uploadedFileRepository.findByOwnerUserUuidAndSha256(ownerUuid, spec.digest.hex)
        var file = existing ?: uploadedFileWriter.createOrFind(ownerUuid, key, spec)
        if (existing != null && file.status == UploadStatus.PENDING) {
            uploadedFiles.findUsable(file.uuid, ownerUuid)
            file = reload(file)
        }

        // 동시에 형식을 정정해도 조건부 갱신에 이긴 상태를 다시 읽는다.
        repeat(3) {
            when (file.status) {
                UploadStatus.READY -> return UploadUrl(file.uuid, null)
                UploadStatus.PENDING -> {
                    if (file.metadata.mimeType != spec.contentType || file.metadata.fileSize != spec.contentLength) {
                        throw InvalidUploadException("같은 파일의 선언 조건이 일치하지 않는다")
                    }
                    val url = issuer.issueUpload(file.storageKey, spec.contentType, spec.contentLength, spec.digest)
                    log.info(
                        "업로드 주소를 발급했다. uuid={} ownerUuid={} type={} bytes={}",
                        file.uuid, ownerUuid, spec.contentType, spec.contentLength,
                    )
                    return UploadUrl(file.uuid, url)
                }
                UploadStatus.REJECTED -> {
                    if (file.metadata.mimeType == spec.contentType) {
                        throw InvalidUploadException("거절된 파일은 올바른 형식으로 다시 선언해야 한다")
                    }
                    uploadedFileWriter.reopen(file, key, spec)
                    file = reload(file)
                }
            }
        }
        throw UploadStateChangedException()
    }

    private fun reload(file: UploadedFile): UploadedFile =
        requireNotNull(uploadedFileRepository.findByUuidAndOwnerUserUuid(file.uuid, file.ownerUserUuid))
}

data class UploadUrl(
    val uuid: UUID,
    /** null 이면 검증된 기존 파일이라 업로드할 필요가 없다. */
    val url: URI?,
)
