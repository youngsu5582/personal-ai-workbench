package dev.joyson.aiworkbench.upload.infrastructure

import dev.joyson.aiworkbench.upload.domain.UploadSpec
import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFileWriteRepository
import dev.joyson.jooq.tables.references.UPLOADED_FILES
import jakarta.persistence.EntityManager
import org.jooq.DSLContext
import org.jooq.JSON
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqUploadedFileWriteRepository(
    private val dsl: DSLContext,
    private val entityManager: EntityManager,
) : UploadedFileWriteRepository {

    override fun insertIfAbsent(uuid: UUID, owner: UUID, key: String, spec: UploadSpec, at: Instant): Int =
        write {
            dsl.insertInto(UPLOADED_FILES)
                .set(UPLOADED_FILES.UUID, uuid)
                .set(UPLOADED_FILES.OWNER_USER_UUID, owner)
                .set(UPLOADED_FILES.STORAGE_KEY, key)
                .set(UPLOADED_FILES.SHA256, spec.digest.hex)
                .set(UPLOADED_FILES.METADATA, metadataOf(spec))
                .set(UPLOADED_FILES.STATUS, UploadStatus.PENDING.name)
                .set(UPLOADED_FILES.CREATED_AT, at.atOffset(ZoneOffset.UTC))
                .set(UPLOADED_FILES.UPDATED_AT, at.atOffset(ZoneOffset.UTC))
                .onConflict(UPLOADED_FILES.OWNER_USER_UUID, UPLOADED_FILES.SHA256)
                .doNothing()
                .execute()
        }

    override fun reopen(id: Long, expectedAt: Instant, key: String, spec: UploadSpec, at: Instant): Int =
        write {
            dsl.update(UPLOADED_FILES)
                .set(UPLOADED_FILES.STORAGE_KEY, key)
                .set(UPLOADED_FILES.METADATA, metadataOf(spec))
                .set(UPLOADED_FILES.STATUS, UploadStatus.PENDING.name)
                .set(UPLOADED_FILES.UPDATED_AT, at.atOffset(ZoneOffset.UTC))
                .where(UPLOADED_FILES.ID.eq(id))
                .and(UPLOADED_FILES.STATUS.eq(UploadStatus.REJECTED.name))
                .and(UPLOADED_FILES.UPDATED_AT.eq(expectedAt.atOffset(ZoneOffset.UTC)))
                .execute()
        }

    private fun metadataOf(spec: UploadSpec): JSON =
        JSON.valueOf("""{"mimeType":"${spec.contentType}","fileSize":${spec.contentLength}}""")

    /** JPA 의 미반영 변경을 먼저 쓰고, jOOQ 갱신 뒤에는 1차 캐시의 옛 상태를 비운다. */
    private fun write(statement: () -> Int): Int {
        entityManager.flush()
        return statement().also { entityManager.clear() }
    }
}
