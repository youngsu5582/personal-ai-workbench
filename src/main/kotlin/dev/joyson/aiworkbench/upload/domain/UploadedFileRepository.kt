package dev.joyson.aiworkbench.upload.domain

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/** 구현은 Spring Data 가 만든다. */
interface UploadedFileRepository : Repository<UploadedFile, Long> {

    fun save(file: UploadedFile): UploadedFile

    fun findByOwnerUserUuidAndSha256(ownerUserUuid: UUID, sha256: String): UploadedFile?

    /** 경합한 요청도 같은 행을 받는다. 유니크 위반으로 트랜잭션이 실패한 뒤 재조회하는 길을 피한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO uploaded_files (uuid, owner_user_uuid, storage_key, sha256, metadata, status, created_at, updated_at)
            VALUES (:uuid, :owner, :key, :sha256, CAST(:metadata AS jsonb), 'PENDING', :at, :at)
            ON CONFLICT (owner_user_uuid, sha256) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("uuid") uuid: UUID,
        @Param("owner") owner: UUID,
        @Param("key") key: String,
        @Param("sha256") sha256: String,
        @Param("metadata") metadata: String,
        @Param("at") at: Instant,
    ): Int

    /** 판정 대상이 바뀌었으면 옛 요청이 새 상태를 덮지 못한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            UPDATE uploaded_files SET storage_key = :key, metadata = CAST(:metadata AS jsonb),
                status = 'PENDING', updated_at = :at
            WHERE id = :id AND status = 'REJECTED' AND updated_at = :expectedAt
        """,
        nativeQuery = true,
    )
    fun reopen(
        @Param("id") id: Long,
        @Param("expectedAt") expectedAt: Instant,
        @Param("key") key: String,
        @Param("metadata") metadata: String,
        @Param("at") at: Instant,
    ): Int

    /** 남의 것은 없는 것과 같게 `null` 이다 — 다르게 답하면 "그 uuid 는 존재한다" 가 새어 나간다. */
    fun findByUuidAndOwnerUserUuid(uuid: UUID, ownerUserUuid: UUID): UploadedFile?

    /**
     * [expected] 상태일 때만 [next] 로 옮긴다. 여러 요청이 같은 행을 동시에 옮겨도 한 번만 바뀐다.
     *
     * `clearAutomatically` 는 같은 트랜잭션에서 다시 읽을 때 1차 캐시의 옛 상태가 돌아오지 않게 한다.
     *
     * @return 실제로 옮긴 행 수. 0 이면 이미 다른 상태다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UploadedFile f SET f.status = :next, f.updatedAt = :at WHERE f.id = :id AND f.status = :expected AND f.updatedAt = :expectedAt")
    fun transition(
        @Param("id") id: Long,
        @Param("expected") expected: UploadStatus,
        @Param("expectedAt") expectedAt: Instant,
        @Param("next") next: UploadStatus,
        @Param("at") at: Instant,
    ): Int
}
