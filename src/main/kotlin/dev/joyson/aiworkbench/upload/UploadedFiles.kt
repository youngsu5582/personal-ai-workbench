package dev.joyson.aiworkbench.upload

import java.net.URI
import java.util.UUID

/**
 * upload 모듈의 공개 경계다 — 올린 파일을 다른 모듈이 입력으로 쓸 때 묻는 곳.
 *
 * 보관소 키는 내주지 않는다. 검증도 읽기도 이 파일을 가진 모듈이 한다.
 */
interface UploadedFiles {

    /**
     * [ownerUuid] 의 업로드 [uuid] 가 입력으로 쓸 수 있는 파일이면 그 형식, 아니면 `null`.
     *
     * 아직 검증하지 않은 파일이면 보관소에서 앞부분을 읽어 검증하고 결과를 남긴다.
     * 그래서 **트랜잭션 밖에서 부른다** — 안에서 부르면 보관소 왕복 동안 커넥션을 붙잡는다.
     *
     * 아직 안 올라왔으면 검증하지 않고 `null` 이다. 다음에 부르면 다시 본다.
     * 없는 것·남의 것·불합격은 구분하지 않는다 — 구분하면 "그 uuid 는 존재한다" 가 새어 나간다.
     */
    fun findUsable(uuid: UUID, ownerUuid: UUID): UsableUpload?

    /**
     * 입력으로 쓸 수 있다고 검증된 업로드를 잠시 받아갈 수 있는 주소. 검증 전이거나 불합격이면 `null` 이다.
     *
     * 여기서는 검증하지 않는다 — 검증을 통과하지 못한 객체에는 열람 경로를 주지 않는다.
     * 주소는 저장해 두지 말고 쓸 때마다 받는다. 대기하는 사이에 만료된다.
     * 보관소가 서명할 수 없어도 `null` 이지만, 직접 업로드를 받는 보관소는 서명도 한다.
     *
     * [fileName] 은 받는 쪽이 저장할 이름이다.
     */
    fun issueReadUrl(uuid: UUID, ownerUuid: UUID, fileName: String): URI?

    /**
     * 입력으로 쓸 수 있다고 검증된 업로드의 바이트. 검증 전이거나 불합격이면, 또는 객체가 없으면 `null` 이다.
     *
     * 받는 쪽이 [issueReadUrl] 의 주소를 받아가지 못할 때 대신 싣는다. 보관소를 읽으므로 트랜잭션 밖에서 부른다.
     */
    fun readContent(uuid: UUID, ownerUuid: UUID): ByteArray?
}

/** 입력으로 쓸 수 있다고 검증된 업로드. */
data class UsableUpload(
    val mimeType: String,
)
