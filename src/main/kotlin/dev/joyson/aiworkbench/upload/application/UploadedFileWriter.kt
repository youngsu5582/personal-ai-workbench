package dev.joyson.aiworkbench.upload.application

import dev.joyson.aiworkbench.upload.domain.UploadSpec
import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFile
import dev.joyson.aiworkbench.upload.domain.UploadedFileMetadata
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 보관소 I/O 는 밖에서 하고, 행 생성과 상태 전이만 트랜잭션으로 감싼다.
 *
 * 트랜잭션이 이 메서드에서 끝나야 [create] 가 유니크 제약에 걸렸을 때 부른 쪽이 새 트랜잭션에서 다시 읽을 수 있다.
 */
@Service
class UploadedFileWriter(
    private val uploadedFileRepository: UploadedFileRepository,
) {
    /** 같은 소유자·해시가 이미 있으면 유니크 제약에 걸린다. */
    @Transactional
    fun create(owner: UUID, key: String, spec: UploadSpec): UploadedFile =
        uploadedFileRepository.save(
            UploadedFile(
                ownerUserUuid = owner,
                sha256 = spec.digest.hex,
                storageKey = key,
                metadata = UploadedFileMetadata(mimeType = spec.contentType, fileSize = spec.contentLength),
            ),
        )

    /** PENDING 인 행을 [next] 로 옮긴다. `false` 면 다른 요청이 먼저 정했다. */
    @Transactional
    fun settle(id: Long, next: UploadStatus): Boolean =
        uploadedFileRepository.transition(id, expected = UploadStatus.PENDING, next = next, at = Instant.now()) > 0
}
