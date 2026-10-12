package dev.joyson.aiworkbench.upload.application

import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 업로드의 상태를 옮긴다. 판정은 [DefaultUploadedFiles] 가 하고, 이 클래스는 쓰기와 트랜잭션만 맡는다.
 *
 * 판정이 보관소를 읽으므로 그 바깥에는 트랜잭션을 걸 수 없다. 트랜잭션은 상태를 옮기는 이 한 번만 감싼다.
 */
@Service
class UploadedFileWriter(
    private val uploadedFileRepository: UploadedFileRepository,
) {

    /** PENDING 인 행을 [next] 로 옮긴다. `false` 면 다른 요청이 먼저 정했다. */
    @Transactional
    fun settle(id: Long, next: UploadStatus): Boolean =
        uploadedFileRepository.transition(id, expected = UploadStatus.PENDING, next = next, at = Instant.now()) > 0
}
