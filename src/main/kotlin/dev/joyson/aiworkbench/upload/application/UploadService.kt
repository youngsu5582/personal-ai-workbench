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
        val file = prepare(findOrCreate(ownerUuid, key, spec), key, spec)

        return when (file.status) {
            UploadStatus.READY -> UploadUrl(file.uuid, null)
            UploadStatus.PENDING -> issuePending(issuer, file, spec)
            UploadStatus.REJECTED -> throw UploadStateChangedException()
        }
    }

    private fun findOrCreate(ownerUuid: UUID, key: String, spec: UploadSpec): UploadedFile {
        val existing = uploadedFileRepository.findByOwnerUserUuidAndSha256(ownerUuid, spec.digest.hex)
            ?: return uploadedFileWriter.createOrFind(ownerUuid, key, spec)

        if (existing.status != UploadStatus.PENDING) return existing
        uploadedFiles.findUsable(existing.uuid, ownerUuid)
        return reload(existing)
    }

    private fun prepare(initial: UploadedFile, key: String, spec: UploadSpec): UploadedFile {
        var file = initial
        // 동시에 형식을 정정해도 조건부 갱신에 이긴 상태를 다시 읽는다.
        repeat(3) {
            if (file.status != UploadStatus.REJECTED) return file
            reopenRejected(file, key, spec)
            file = reload(file)
        }
        throw UploadStateChangedException()
    }

    private fun reopenRejected(file: UploadedFile, key: String, spec: UploadSpec) {
        if (file.metadata.mimeType == spec.contentType) {
            throw InvalidUploadException("거절된 파일은 올바른 형식으로 다시 선언해야 한다")
        }
        uploadedFileWriter.reopen(file, key, spec)
    }

    private fun issuePending(issuer: PresignedUploadIssuer, file: UploadedFile, spec: UploadSpec): UploadUrl {
        if (file.metadata.mimeType != spec.contentType || file.metadata.fileSize != spec.contentLength) {
            throw InvalidUploadException("같은 파일의 선언 조건이 일치하지 않는다")
        }
        val url = issuer.issueUpload(file.storageKey, spec.contentType, spec.contentLength, spec.digest)
        log.info(
            "업로드 주소를 발급했다. uuid={} ownerUuid={} type={} bytes={}",
            file.uuid, file.ownerUserUuid, spec.contentType, spec.contentLength,
        )
        return UploadUrl(file.uuid, url)
    }

    private fun reload(file: UploadedFile): UploadedFile =
        requireNotNull(uploadedFileRepository.findByUuidAndOwnerUserUuid(file.uuid, file.ownerUserUuid))
}

data class UploadUrl(
    val uuid: UUID,
    /** null 이면 검증된 기존 파일이라 업로드할 필요가 없다. */
    val url: URI?,
)
