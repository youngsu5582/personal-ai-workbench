package dev.joyson.aiworkbench.generation.domain

import java.util.UUID

/** 내려보내는 데 필요한 것만. 엔티티를 통째로 읽을 이유가 없다. */
data class FileLocation(
    val storageKey: String,
    val mimeType: String,
)

/**
 * 파일을 **소유자와 함께** 찾는다.
 *
 * 남의 파일은 없는 것과 구분되지 않아야 한다. 다르게 답하면 "그 uuid 는 존재한다" 가 새어 나간다.
 * 구현은 infrastructure 의 `JooqGeneratedFileFinder` 다.
 */
interface GeneratedFileFinder {

    fun findOwned(fileUuid: UUID, ownerUuid: UUID): FileLocation?
}
