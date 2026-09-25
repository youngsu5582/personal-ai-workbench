package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.downloadFileNameOf
import dev.joyson.aiworkbench.generation.infrastructure.FileLocation
import dev.joyson.aiworkbench.generation.infrastructure.GeneratedFileFinder
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import org.springframework.stereotype.Service
import java.net.URI
import java.util.UUID

/**
 * 결과물을 어떻게 내려보낼지.
 *
 * 두 길이 있는 이유는 **보관소마다 할 수 있는 일이 다르기 때문**이다. 어느 쪽이든
 * 소유권 확인은 이미 끝난 뒤다.
 */
sealed interface FileDownload {

    /**
     * 보관소가 직접 내준다. **바이트가 우리를 통과하지 않는다.**
     *
     * 이 주소는 그 자체가 통행증이라, 우리가 인가를 마친 뒤에만 만들어진다.
     */
    data class Redirect(val url: URI) : FileDownload

    /** 우리가 읽어서 내보낸다. 보관소가 주소를 줄 수 없을 때의 길이다. */
    class Streamed(
        val content: ByteArray,
        val contentType: String,
        val fileName: String,
    ) : FileDownload
}

@Service
class GeneratedFileReader(
    private val generatedFileFinder: GeneratedFileFinder,
    private val fileStorage: FileStorage,
    /** 보관소가 주소에 서명할 수 있을 때만 있다. 로컬 디스크는 못 한다. */
    private val presignedUrlIssuer: PresignedUrlIssuer?,
) {

    /**
     * 자기 파일을 내려받을 방법. 남의 것이면 `null` — 없는 것과 구분되지 않아야 한다.
     *
     * `@Transactional` 을 걸지 않는다. 조회가 단건이라 Spring Data 가 스스로 열고 닫는데,
     * 여기서 열면 **보관소 접근까지 그 안에 들어온다.** 네트워크 왕복하는 동안 DB 커넥션을 잡을 이유가 없다.
     *
     * 두 길을 순서대로 시도한다 — **보관소가 직접 내줄 수 있으면 그쪽이 낫다.**
     * 발급자가 있으면 바이트를 읽지 않아, 수 MB 가 앱 힙에 올라왔다 내려가는 일이 사라진다.
     */
    fun download(fileUuid: UUID, ownerUuid: UUID): FileDownload? {
        // 인가는 여기 한 곳뿐이다. 아래 두 길은 이미 확인된 위치만 받는다.
        val location = generatedFileFinder.findOwned(fileUuid, ownerUuid) ?: return null
        val fileName = downloadFileNameOf(fileUuid, location.mimeType)

        return redirectTo(location, fileName) ?: streamFrom(location, fileName)
    }

    /** 보관소가 주소에 서명할 수 있을 때. 바이트를 읽지 않는다. */
    private fun redirectTo(location: FileLocation, fileName: String): FileDownload.Redirect? =
        presignedUrlIssuer?.let { FileDownload.Redirect(it.issue(location.storageKey, fileName)) }

    /** 서명할 수 없을 때. 행은 있는데 보관소에 없으면 없는 것으로 답한다. */
    private fun streamFrom(location: FileLocation, fileName: String): FileDownload.Streamed? =
        fileStorage.read(location.storageKey)?.let {
            FileDownload.Streamed(content = it, contentType = location.mimeType, fileName = fileName)
        }
}
