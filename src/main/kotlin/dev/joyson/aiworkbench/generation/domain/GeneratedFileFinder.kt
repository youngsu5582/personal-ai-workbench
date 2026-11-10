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
 * 남의 파일은 없는 것과 구분되지 않아야 한다. 다르게 답하면 "그 해시의 파일은 존재한다" 가 새어 나간다.
 * 구현은 infrastructure 의 `JooqGeneratedFileFinder` 다.
 */
interface GeneratedFileFinder {

    fun findOwned(sha256: String, workspaceUuid: UUID): FileLocation?

    /** 여러 장을 한 번에 찾는다. 찾은 해시만 키로 담기고, 같은 내용이 여러 번 만들어졌어도 해시마다 하나다. */
    fun findOwned(sha256s: Collection<String>, workspaceUuid: UUID): Map<String, FileLocation>
}
