package dev.joyson.aiworkbench.upload.domain

import java.time.Instant
import java.util.UUID

/** 유니크 충돌과 상태 경합을 조건부 쓰기로 처리한다. */
interface UploadedFileWriteRepository {

    /** 같은 소유자·해시가 이미 있으면 0, 새 행을 만들면 1을 반환한다. */
    fun insertIfAbsent(uuid: UUID, owner: UUID, key: String, spec: UploadSpec, at: Instant): Int

    /** 읽었던 REJECTED 상태와 시각이 그대로일 때만 선언을 정정한다. */
    fun reopen(id: Long, expectedAt: Instant, key: String, spec: UploadSpec, at: Instant): Int
}
