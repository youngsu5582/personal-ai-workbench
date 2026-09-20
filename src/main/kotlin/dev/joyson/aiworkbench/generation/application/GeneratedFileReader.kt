package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.MimeTypes
import dev.joyson.aiworkbench.generation.infrastructure.GeneratedFileFinder
import dev.joyson.aiworkbench.storage.FileStorage
import org.springframework.stereotype.Service
import java.util.UUID

/** 내려보낼 준비가 끝난 파일. */
class DownloadedFile(
    val content: ByteArray,
    val contentType: String,
    val fileName: String,
)

@Service
class GeneratedFileReader(
    private val generatedFileFinder: GeneratedFileFinder,
    private val fileStorage: FileStorage,
) {

    /**
     * 자기 파일을 읽는다. 남의 것이면 `null` — 없는 것과 구분되지 않아야 한다.
     *
     * `@Transactional` 을 걸지 않는다. 조회가 단건이라 Spring Data 가 스스로 열고 닫는데,
     * 여기서 열면 **보관소 읽기까지 그 안에 들어온다.** 지금은 로컬 디스크라 빠르지만
     * 오브젝트 스토어로 바뀌면 네트워크 왕복이고, 그 시간만큼 DB 커넥션을 잡을 이유가 없다.
     *
     * 서명된 URL 을 쓰게 되면 이 메서드는 사라진다 — 바이트가 우리를 통과하지 않는다.
     */
    fun download(fileUuid: UUID, ownerUuid: UUID): DownloadedFile? {
        val location = generatedFileFinder.findOwned(fileUuid, ownerUuid) ?: return null
        val content = fileStorage.read(location.storageKey) ?: return null

        return DownloadedFile(
            content = content,
            contentType = location.mimeType,
            // 브라우저가 저장할 때 쓰는 이름. 보관소 키와 무관하게 짓는다 —
            // 키가 내용 기반(해시)으로 바뀌면 거기엔 확장자도 뜻도 없다.
            // uuid 를 쓰는 것은 받는 사람이 요청한 주소와 파일명이 같아지기 때문이다.
            fileName = "$fileUuid.${MimeTypes.extensionOf(location.mimeType)}",
        )
    }
}
