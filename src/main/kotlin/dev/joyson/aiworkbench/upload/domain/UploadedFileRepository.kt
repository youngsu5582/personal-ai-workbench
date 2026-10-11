package dev.joyson.aiworkbench.upload.domain

import org.springframework.data.repository.Repository
import java.util.UUID

/** 구현은 Spring Data 가 만든다. */
interface UploadedFileRepository : Repository<UploadedFile, Long> {

    fun save(file: UploadedFile): UploadedFile

    /** 남의 것은 없는 것과 같게 `null` 이다 — 다르게 답하면 "그 uuid 는 존재한다" 가 새어 나간다. */
    fun findByUuidAndOwnerUserUuid(uuid: UUID, ownerUserUuid: UUID): UploadedFile?
}
