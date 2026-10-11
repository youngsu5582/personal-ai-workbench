package dev.joyson.aiworkbench.upload.application

import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.upload.UploadedFiles
import dev.joyson.aiworkbench.upload.UsableUpload
import dev.joyson.aiworkbench.upload.domain.ImageContentType
import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFile
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 올린 파일을 처음 쓰일 때 판정한다 — 있는가, 선언한 크기인가, 앞부분이 선언한 형식의 매직넘버인가.
 *
 * 판정은 한 번이다. READY 와 REJECTED 는 다시 보지 않는다.
 */
@Service
class DefaultUploadedFiles(
    private val uploadedFileRepository: UploadedFileRepository,
    private val uploadedFileWriter: UploadedFileWriter,
    private val fileStorage: FileStorage,
) : UploadedFiles {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun findUsable(uuid: UUID, ownerUuid: UUID): UsableUpload? {
        val file = uploadedFileRepository.findByUuidAndOwnerUserUuid(uuid, ownerUuid) ?: return null
        return when (file.status) {
            UploadStatus.READY -> file.toUsable()
            UploadStatus.REJECTED -> null
            UploadStatus.PENDING -> judge(file)
        }
    }

    private fun judge(file: UploadedFile): UsableUpload? {
        val leading = fileStorage.readRange(file.storageKey, 0L until ImageContentType.SIGNATURE_LENGTH) ?: return null

        val detected = ImageContentType.detect(leading.bytes)
        val matches = leading.totalSize == file.metadata.fileSize && detected == file.metadata.mimeType
        val next = if (matches) UploadStatus.READY else UploadStatus.REJECTED

        if (!uploadedFileWriter.settle(requireNotNull(file.id), next)) {
            // 다른 요청이 먼저 정했다. 같은 바이트를 봤다면 판정도 같지만, 그 사이 덮어쓰였을 수 있어 그쪽을 따른다.
            return uploadedFileRepository.findByUuidAndOwnerUserUuid(file.uuid, file.ownerUserUuid)
                ?.takeIf { it.status == UploadStatus.READY }
                ?.toUsable()
        }

        log.info(
            "업로드를 판정했다. uuid={} status={} declared={}/{} actual={}/{}",
            file.uuid, next, file.metadata.mimeType, file.metadata.fileSize, detected, leading.totalSize,
        )
        if (matches) return file.toUsable()

        // 옮기는 데 이긴 쪽만 지운다. 지우지 못해도 REJECTED 라 이 객체를 읽을 길이 없어, 판정을 실패로 돌리지 않는다.
        runCatching { fileStorage.delete(file.storageKey) }
            .onFailure { log.warn("불합격한 업로드를 지우지 못했다. uuid={}", file.uuid, it) }
        return null
    }

    private fun UploadedFile.toUsable() = UsableUpload(mimeType = metadata.mimeType)
}
