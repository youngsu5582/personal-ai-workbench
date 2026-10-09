package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.FileLocation
import dev.joyson.aiworkbench.generation.domain.GeneratedFileFinder
import dev.joyson.aiworkbench.generation.domain.MimeTypes
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.generation.domain.option.FileSource
import dev.joyson.aiworkbench.generation.domain.option.ImageToImageOption
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import org.springframework.stereotype.Component
import java.net.URI
import java.util.UUID

/**
 * 소유권을 확인한 참조를 바이트 또는 읽기 주소로 푼다.
 *
 * [GeneratedFileService] 와 같은 모양이다 — 조회는 finder 에, 읽기는 보관소에 맡기고
 * 둘을 잇기만 한다. `@Transactional` 을 걸지 않는 이유도 같다: 보관소 읽기가 그 안에 들어온다.
 *
 * 찾지 못하면 **예외를 던진다.** finder 가 `null` 을 답하는 것과 어긋나 보이지만, 그 규칙의 근거는
 * "호출 지점마다 처리가 갈린다" 였다. 여기는 부르는 곳이 워커 하나뿐이고 한 장이라도 없으면
 * 요청 전체가 실패하므로, 항목별 `null` 은 부르는 쪽만 복잡하게 만든다.
 */
@Component
class FileSourceResolver(
    private val generatedFileFinder: GeneratedFileFinder,
    private val fileStorage: FileStorage,
    private val presignedUrlIssuer: PresignedUrlIssuer? = null,
) {

    /**
     * 참조가 가리키는 파일의 자리를 **소유자와 함께** 찾는다. 남의 것은 없는 것과 같게 `null` 이다.
     *
     * **바이트는 읽지 않는다.** 접수는 트랜잭션 안이라 보관소 왕복을 넣으면 안 되고,
     * 존재를 확인하는 데는 메타 행이면 충분하다. 접수는 400 을 만들고 워커는 Task 를 닫으므로,
     * 찾지 못한 것을 어떻게 할지는 부르는 쪽이 정한다.
     */
    fun findOwned(source: FileSource, ownerUuid: UUID): FileLocation? = when (source) {
        // sealed 라 참조의 종류가 늘면 여기가 컴파일 에러로 드러난다.
        is FileSource.Generated -> generatedFileFinder.findOwned(source.uuid, ownerUuid)
    }

    /**
     * 실행 시점에 입력 파일의 바이트 또는 새 읽기 주소를 준비한다.
     *
     * 입력을 요구하지 않는 종류면 빈 목록이다 — 읽을 것이 없지, 못 읽은 것이 아니다.
     */
    fun resolve(ownerUuid: UUID, option: GenerationOption): List<ResolvedFile> = when (option) {
        is TextToImageOption -> emptyList()
        is ImageToImageOption -> option.sources.map { prepare(it, ownerUuid) }
    }

    private fun prepare(source: FileSource, ownerUuid: UUID): ResolvedFile {
        // 없는 것과 남의 것은 동일하게 처리한다.(외부 노출 방지) 주소를 발급하기 전에 확인해야 한다.
        val location = findOwned(source, ownerUuid)
            ?: throw FileSourceUnavailableException(
                retryable = false,
                message = "입력 이미지를 찾을 수 없다: ${source.uuid}",
            )

        val filename = "${source.uuid}.${MimeTypes.extensionOf(location.mimeType)}"
        presignedUrlIssuer?.let { issuer ->
            // 큐에는 UUID만 저장한다. 실행과 재시도마다 발급해야 대기 중 주소가 만료되지 않는다.
            val url = try {
                issuer.issue(location.storageKey, filename)
            } catch (e: Exception) {
                // 서명 실패는 Provider를 부르기 전의 실패다. 원문에 자격증명이 섞일 수 있어 사유만 남긴다.
                throw FileSourceUnavailableException(true, "입력 이미지 주소를 발급하지 못했다: ${source.uuid}", e)
            }
            return ResolvedFile.Url(url, location.mimeType, filename)
        }

        // 행은 있는데 바이트가 없는 상태다. 스스로 낫지 않는다.
        // 보관소 자체의 장애는 FileStorage 가 FileStorageException 으로 말하고, 그건 부르는 쪽이 받는다.
        val bytes = fileStorage.read(location.storageKey)
            ?: throw FileSourceUnavailableException(
                retryable = false,
                message = "입력 이미지의 내용이 없다: ${source.uuid}",
            )

        return ResolvedFile.Bytes(
            bytes = bytes,
            mimeType = location.mimeType,
            // 보관소 키를 파일명으로 쓰지 않는다. 키는 내용 주소라 확장자 말고는 뜻이 없고,
            // 받는 쪽 로그에는 무엇을 보냈는지 가리키는 이름이 남는 편이 낫다.
            filename = filename,
        )
    }
}

/**
 * 입력 파일을 쓸 수 없다.
 *
 * [retryable] 의 뜻은 `ExternalApiException` · `FileStorageException` 과 같다 —
 * 사실일 뿐이고, 실제로 재시도할지는 부르는 쪽이 정한다.
 */
class FileSourceUnavailableException(
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * 실행 시점에 준비한 입력 파일의 바이트 또는 읽기 주소.
 *
 * 포트의 `ExternalApiFileInput` 과 모양이 같지만 여기서 그것을 만들지 않는다 —
 * 두 어휘를 잇는 책임은 [ProviderRequestMapper] 한 곳에 있고, 포트를 아는 자리가 둘이 되면
 * 포트에 필드가 늘 때 고칠 곳도 둘이 된다.
 *
 * 바이트는 배열의 참조 비교를 피하고, 주소는 toString으로 읽기 권한이 새지 않도록 일반 클래스로 둔다.
 */
sealed interface ResolvedFile {
    val mimeType: String
    val filename: String

    class Bytes(val bytes: ByteArray, override val mimeType: String, override val filename: String) : ResolvedFile

    /** 읽기 권한을 가진 주소는 자동 생성된 toString으로도 출력하지 않는다. */
    class Url(val url: URI, override val mimeType: String, override val filename: String) : ResolvedFile
}
