package dev.joyson.aiworkbench.generation.domain

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import java.util.UUID

/** 내려보내는 데 필요한 것만. 엔티티를 통째로 읽을 이유가 없다. */
data class FileLocation(
    val storageKey: String,
    val mimeType: String,
)

/** 결과 파일 하나와 그 파일을 만든 작업. 고른 순서만 정하고, 엔티티는 부르는 쪽이 식별자로 읽는다. */
data class OwnedFileRef(
    val fileId: Long,
    val jobId: Long,
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

    /**
     * 이 서비스가 만든 [kind] 의 결과 파일을 최신순으로 나누어 찾는다.
     *
     * 같은 내용이 여러 번 만들어졌으면 만든 횟수만큼 나온다 — 항목은 해시가 아니라 생성 이력 하나다.
     */
    fun findAllOwned(workspaceUuid: UUID, kind: FileKind, pageable: Pageable): Slice<OwnedFileRef>
}
