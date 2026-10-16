package dev.joyson.aiworkbench.upload.application

import dev.joyson.aiworkbench.upload.domain.UploadSpec
import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFile
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import dev.joyson.aiworkbench.upload.domain.UploadedFileWriteRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** 보관소 I/O 는 밖에서 하고, 행 생성과 조건부 갱신만 트랜잭션으로 감싼다. */
@Service
class UploadedFileWriter(
    private val uploadedFileRepository: UploadedFileRepository,
    private val uploadedFileWriteRepository: UploadedFileWriteRepository,
) {
    @Transactional
    fun createOrFind(owner: UUID, key: String, spec: UploadSpec): UploadedFile {
        uploadedFileWriteRepository.insertIfAbsent(
            uuid = UUID.randomUUID(), owner = owner, key = key, spec = spec, at = Instant.now(),
        )
        return requireNotNull(uploadedFileRepository.findByOwnerUserUuidAndSha256(owner, spec.digest.hex))
    }

    /** 읽을 때의 상태와 시각이 같아야 판정을 반영한다. 정정된 파일에 옛 판정이 끼지 못한다. */
    @Transactional
    fun settle(file: UploadedFile, next: UploadStatus): Boolean =
        uploadedFileRepository.transition(
            requireNotNull(file.id), expected = UploadStatus.PENDING,
            expectedAt = file.updatedAt, next = next, at = Instant.now(),
        ) > 0

    @Transactional
    fun reopen(file: UploadedFile, key: String, spec: UploadSpec): Boolean =
        uploadedFileWriteRepository.reopen(
            requireNotNull(file.id), file.updatedAt, key, spec, Instant.now(),
        ) > 0
}
