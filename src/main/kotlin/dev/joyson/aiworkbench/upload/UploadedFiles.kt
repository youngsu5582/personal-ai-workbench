package dev.joyson.aiworkbench.upload

import java.util.UUID

/**
 * upload 모듈의 공개 경계다 — 올린 파일을 다른 모듈이 입력으로 쓸 때 묻는 곳.
 *
 * 보관소 키는 내주지 않는다. 판정도 읽기도 이 파일을 가진 모듈이 한다.
 */
interface UploadedFiles {

    /**
     * [ownerUuid] 의 업로드 [uuid] 가 입력으로 쓸 수 있는 파일이면 그 형식, 아니면 `null`.
     *
     * 아직 판정하지 않은 파일이면 보관소에서 앞부분을 읽어 판정하고 결과를 남긴다.
     * 그래서 **트랜잭션 밖에서 부른다** — 안에서 부르면 보관소 왕복 동안 커넥션을 붙잡는다.
     *
     * 아직 안 올라왔으면 판정하지 않고 `null` 이다. 다음에 부르면 다시 본다.
     * 없는 것·남의 것·불합격은 구분하지 않는다 — 구분하면 "그 uuid 는 존재한다" 가 새어 나간다.
     */
    fun findUsable(uuid: UUID, ownerUuid: UUID): UsableUpload?
}

/** 입력으로 쓸 수 있다고 판정된 업로드. */
data class UsableUpload(
    val mimeType: String,
)
