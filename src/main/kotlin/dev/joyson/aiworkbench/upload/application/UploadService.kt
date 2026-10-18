package dev.joyson.aiworkbench.upload.application

import dev.joyson.aiworkbench.storage.BlobKey
import dev.joyson.aiworkbench.storage.PresignedUploadIssuer
import dev.joyson.aiworkbench.upload.domain.UploadSpec
import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFile
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
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

    /**
     * 처음 보는 파일이면 행을 만들고 업로드 주소를 낸다.
     *
     * 같은 파일(소유자·해시)을 이미 발급했으면, 그사이 올라왔을 수 있으니 검증한 결과대로 답한다.
     * - READY: 검증된 파일이 있다. 업로드할 필요가 없다.
     * - PENDING: 아직 올라오지 않았다. 같은 위치의 주소를 다시 낸다.
     * - REJECTED: 다시 열지 않는다. 형식은 클라이언트가 매직넘버로 정해 선언하는 것이 계약이라,
     *   선언을 틀린 요청을 서버가 정정해 주지 않고 거절 사유만 알린다.
     */
    fun issue(ownerUuid: UUID, spec: UploadSpec): UploadUrl? {
        val issuer = presignedUploadIssuer ?: return null
        val existing = uploadedFileRepository.findByOwnerUserUuidAndSha256(ownerUuid, spec.digest.hex)
            ?: return issuePending(issuer, create(ownerUuid, spec), spec)

        return when (uploadedFiles.verify(existing)) {
            UploadStatus.READY -> reuse(existing)
            UploadStatus.PENDING -> issuePending(issuer, existing, spec)
            UploadStatus.REJECTED -> reject(existing, spec)
        }
    }

    private fun reuse(file: UploadedFile): UploadUrl {
        log.info("검증된 같은 파일을 재사용한다. sha256={} ownerUuid={}", file.sha256, file.ownerUserUuid)
        return UploadUrl(file.sha256, null)
    }

    /** 공식 클라이언트는 서버와 같은 규칙으로 형식을 선언해 여기 오지 않는다. 오면 규칙이 어긋났거나 다른 소비자다. */
    private fun reject(file: UploadedFile, spec: UploadSpec): Nothing {
        log.warn(
            "거절된 파일을 다시 발급하려 했다. sha256={} ownerUuid={} rejected={} requested={}",
            file.sha256, file.ownerUserUuid, file.metadata.mimeType, spec.contentType,
        )
        throw InvalidUploadException(
            "내용이 선언한 형식과 달라 거절된 파일이다. 형식은 확장자가 아니라 파일 앞부분으로 정해야 하며, 같은 내용은 다시 올릴 수 없다",
        )
    }

    /**
     * 같은 파일을 동시에 처음 올리면 한쪽이 유니크 제약에 걸린다.
     * 그건 오류가 아니라 "상대가 먼저 만들었다" 는 뜻이므로 다시 읽어서 그 행을 쓴다.
     */
    private fun create(ownerUuid: UUID, spec: UploadSpec): UploadedFile {
        val key = BlobKey.uploaded(ownerUuid, spec.digest, spec.contentType).value
        return try {
            uploadedFileWriter.create(ownerUuid, key, spec)
        } catch (e: DataIntegrityViolationException) {
            uploadedFileRepository.findByOwnerUserUuidAndSha256(ownerUuid, spec.digest.hex) ?: throw e
        }
    }

    private fun issuePending(issuer: PresignedUploadIssuer, file: UploadedFile, spec: UploadSpec): UploadUrl {
        if (file.metadata.mimeType != spec.contentType || file.metadata.fileSize != spec.contentLength) {
            throw InvalidUploadException("같은 파일의 선언 조건이 일치하지 않는다")
        }
        val url = issuer.issueUpload(file.storageKey, spec.contentType, spec.contentLength, spec.digest)
        log.info(
            "업로드 주소를 발급했다. sha256={} ownerUuid={} type={} bytes={}",
            file.sha256, file.ownerUserUuid, spec.contentType, spec.contentLength,
        )
        return UploadUrl(file.sha256, url)
    }
}

data class UploadUrl(
    val sha256: String,
    /** null 이면 검증된 기존 파일이라 업로드할 필요가 없다. */
    val url: URI?,
)
