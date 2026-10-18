package dev.joyson.aiworkbench.upload.application

import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import dev.joyson.aiworkbench.upload.UploadedFiles
import dev.joyson.aiworkbench.upload.UsableUpload
import dev.joyson.aiworkbench.upload.domain.ImageContentType
import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFile
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.util.UUID

/**
 * 올린 파일을 검증하고([verify]), 검증을 통과한 파일에만 읽기 주소와 바이트를 낸다.
 *
 * 업로드 상태가 바뀌는 곳은 [verify] 하나다. 생명주기는 [UploadStatus] 에 있다.
 */
@Service
class DefaultUploadedFiles(
    private val uploadedFileRepository: UploadedFileRepository,
    private val uploadedFileWriter: UploadedFileWriter,
    private val fileStorage: FileStorage,
    private val presignedUrlIssuer: PresignedUrlIssuer? = null,
) : UploadedFiles {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun findUsable(sha256: String, ownerUuid: UUID): UsableUpload? {
        val file = uploadedFileRepository.findByOwnerUserUuidAndSha256(ownerUuid, sha256) ?: return null
        return if (verify(file) == UploadStatus.READY) file.toUsable() else null
    }

    override fun issueReadUrl(sha256: String, ownerUuid: UUID, fileName: String): URI? {
        val issuer = presignedUrlIssuer ?: return null
        val file = findReady(sha256, ownerUuid) ?: return null
        return issuer.issue(file.storageKey, fileName)
    }

    override fun readContent(sha256: String, ownerUuid: UUID): ByteArray? =
        findReady(sha256, ownerUuid)?.let { fileStorage.read(it.storageKey) }

    /** 검증을 통과한 것만 내준다. 검증 전 객체에는 어떤 경로로도 닿지 않게 한다. */
    private fun findReady(sha256: String, ownerUuid: UUID): UploadedFile? =
        uploadedFileRepository.findByOwnerUserUuidAndSha256(ownerUuid, sha256)
            ?.takeIf { it.status == UploadStatus.READY }

    /**
     * PENDING 인 파일을 검증해 READY 나 REJECTED 로 옮기고, 지금 상태를 돌려준다.
     *
     * 보관소에서 앞부분을 읽어 선언한 크기·형식과 대조한다. 객체가 아직 없으면 검증하지 않고 PENDING 이다.
     * READY 와 REJECTED 는 다시 보지 않는다. 보관소를 읽으므로 트랜잭션 밖에서 부른다.
     */
    fun verify(file: UploadedFile): UploadStatus {
        if (file.status.isFinal) return file.status
        val leading = fileStorage.readRange(file.storageKey, 0L until ImageContentType.SIGNATURE_LENGTH)
            ?: return UploadStatus.PENDING

        val detected = ImageContentType.detect(leading.bytes)
        val matches = leading.totalSize == file.metadata.fileSize && detected == file.metadata.mimeType
        val next = if (matches) UploadStatus.READY else UploadStatus.REJECTED

        if (!uploadedFileWriter.settle(requireNotNull(file.id), next)) {
            // 다른 요청이 먼저 정했다. 그쪽 검증을 따른다.
            return requireNotNull(uploadedFileRepository.findByOwnerUserUuidAndSha256(file.ownerUserUuid, file.sha256)).status
        }

        log.info(
            "업로드를 검증했다. sha256={} status={} declared={}/{} actual={}/{}",
            file.sha256, next, file.metadata.mimeType, file.metadata.fileSize, detected, leading.totalSize,
        )
        if (next == UploadStatus.REJECTED) {
            // 옮기는 데 이긴 쪽만 지운다. 지우지 못해도 REJECTED 라 이 객체를 읽을 길이 없어, 검증을 실패로 돌리지 않는다.
            runCatching { fileStorage.delete(file.storageKey) }
                .onFailure { log.warn("불합격한 업로드를 지우지 못했다. sha256={}", file.sha256, it) }
        }
        return next
    }

    private fun UploadedFile.toUsable() = UsableUpload(mimeType = metadata.mimeType)
}
