package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.downloadFileNameOf
import dev.joyson.aiworkbench.generation.domain.FileKind
import dev.joyson.aiworkbench.generation.domain.FileLocation
import dev.joyson.aiworkbench.generation.domain.GeneratedFileFinder
import dev.joyson.aiworkbench.generation.domain.GeneratedFileRepository
import dev.joyson.aiworkbench.generation.domain.GenerationJobRepository
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import org.springframework.data.domain.PageRequest
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
class GeneratedFileService(
    private val generatedFileFinder: GeneratedFileFinder,
    private val generatedFileRepository: GeneratedFileRepository,
    private val generationJobRepository: GenerationJobRepository,
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
    fun download(sha256: String, workspaceUuid: UUID): FileDownload? {
        // 인가는 여기 한 곳뿐이다. 아래 두 길은 이미 확인된 위치만 받는다.
        val location = generatedFileFinder.findOwned(sha256, workspaceUuid) ?: return null
        val fileName = downloadFileNameOf(sha256, location.mimeType)

        return redirectTo(location, fileName) ?: streamFrom(location, fileName)
    }

    /**
     * 이 서비스가 만든 [kind] 의 결과 파일을 최신순으로 나누어 준다.
     *
     * 페이지는 작업이 아니라 **파일** 단위다. 작업 단위로 나누면 글 작업이 많은 쪽에서 빈 페이지가 생긴다.
     * 고르는 것과 읽는 것을 나눈다 — 고르기는 소유자까지 조인하는 [GeneratedFileFinder] 가, 읽기는 엔티티 매핑이 맡는다.
     */
    fun listOwned(workspaceUuid: UUID, kind: FileKind, page: Int, size: Int): OwnedFileListView {
        val refs = generatedFileFinder.findAllOwned(workspaceUuid, kind, PageRequest.of(page, size))
        if (refs.content.isEmpty()) return OwnedFileListView(items = emptyList(), hasNext = false)
        val files = generatedFileRepository.findAllByIdIn(refs.content.map { it.fileId }).associateBy { it.id }
        val jobs = generationJobRepository.findAllByIdIn(refs.content.map { it.jobId }.distinct()).associateBy { it.id }
        // 결과 파일은 지우지 않으니, 방금 고른 행은 읽을 때도 있다.
        val items = refs.content.map { ref ->
            val file = files.getValue(ref.fileId)
            val job = jobs.getValue(ref.jobId)
            OwnedFileView(
                file = file.toView(presignedUrlIssuer),
                createdAt = file.createdAt,
                job = FileJobView(uuid = job.uuid, model = job.model, option = job.option),
            )
        }
        return OwnedFileListView(items = items, hasNext = refs.hasNext())
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
