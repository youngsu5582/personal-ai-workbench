package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.MimeTypes
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.generation.domain.option.FileSource
import dev.joyson.aiworkbench.generation.domain.option.ImageToImageOption
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.generation.infrastructure.FileSourceFinder
import dev.joyson.aiworkbench.generation.infrastructure.ResolvedFile
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 소유권을 확인한 참조를 바이트 또는 읽기 주소로 푼다.
 *
 * [GeneratedFileReader] 와 같은 모양이다 — 조회는 finder 에, 읽기는 보관소에 맡기고
 * 둘을 잇기만 한다. `@Transactional` 을 걸지 않는 이유도 같다: 보관소 읽기가 그 안에 들어온다.
 *
 * 찾지 못하면 **예외를 던진다.** finder 가 `null` 을 답하는 것과 어긋나 보이지만, 그 규칙의 근거는
 * "호출 지점마다 처리가 갈린다" 였다. 여기는 부르는 곳이 워커 하나뿐이고 한 장이라도 없으면
 * 요청 전체가 실패하므로, 항목별 `null` 은 부르는 쪽만 복잡하게 만든다.
 */
@Component
class FileSourceResolver(
    private val fileSourceFinder: FileSourceFinder,
    private val fileStorage: FileStorage,
    private val presignedUrlIssuer: PresignedUrlIssuer? = null,
) {

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
        val location = fileSourceFinder.findOwned(source, ownerUuid)
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
