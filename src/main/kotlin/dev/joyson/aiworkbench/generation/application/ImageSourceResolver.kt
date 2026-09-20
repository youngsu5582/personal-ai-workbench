package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.MimeTypes
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.generation.domain.option.ImageSource
import dev.joyson.aiworkbench.generation.domain.option.ImageToImageOption
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.generation.infrastructure.ImageSourceFinder
import dev.joyson.aiworkbench.generation.infrastructure.ResolvedImage
import dev.joyson.aiworkbench.storage.FileStorage
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 참조를 실제 바이트로 푼다.
 *
 * [GeneratedFileReader] 와 같은 모양이다 — 조회는 finder 에, 읽기는 보관소에 맡기고
 * 둘을 잇기만 한다. `@Transactional` 을 걸지 않는 이유도 같다: 보관소 읽기가 그 안에 들어온다.
 *
 * 찾지 못하면 **예외를 던진다.** finder 가 `null` 을 답하는 것과 어긋나 보이지만, 그 규칙의 근거는
 * "호출 지점마다 처리가 갈린다" 였다. 여기는 부르는 곳이 워커 하나뿐이고 한 장이라도 없으면
 * 요청 전체가 실패하므로, 항목별 `null` 은 부르는 쪽만 복잡하게 만든다.
 */
@Component
class ImageSourceResolver(
    private val imageSourceFinder: ImageSourceFinder,
    private val fileStorage: FileStorage,
) {

    /**
     * 이 옵션이 필요로 하는 입력 이미지를 모두 읽는다.
     *
     * 입력을 요구하지 않는 종류면 빈 목록이다 — 읽을 것이 없지, 못 읽은 것이 아니다.
     */
    fun resolve(ownerUuid: UUID, option: GenerationOption): List<ResolvedImage> = when (option) {
        is TextToImageOption -> emptyList()
        is ImageToImageOption -> option.sources.map { read(it, ownerUuid) }
    }

    private fun read(source: ImageSource, ownerUuid: UUID): ResolvedImage {
        // 없는 것과 남의 것이 같은 null 로 떨어진다. 어느 쪽이든 다시 읽어도 같으므로 재시도 대상이 아니다.
        val location = imageSourceFinder.findOwned(source, ownerUuid)
            ?: throw ImageSourceUnavailableException(
                retryable = false,
                message = "입력 이미지를 찾을 수 없다: ${source.uuid}",
            )

        // 행은 있는데 바이트가 없는 상태다. 스스로 낫지 않는다.
        // 보관소 자체의 장애는 FileStorage 가 FileStorageException 으로 말하고, 그건 부르는 쪽이 받는다.
        val bytes = fileStorage.read(location.storageKey)
            ?: throw ImageSourceUnavailableException(
                retryable = false,
                message = "입력 이미지의 내용이 없다: ${source.uuid}",
            )

        return ResolvedImage(
            bytes = bytes,
            mimeType = location.mimeType,
            // 보관소 키를 파일명으로 쓰지 않는다. 키는 내용 주소라 확장자 말고는 뜻이 없고,
            // 받는 쪽 로그에는 무엇을 보냈는지 가리키는 이름이 남는 편이 낫다.
            filename = "${source.uuid}.${MimeTypes.extensionOf(location.mimeType)}",
        )
    }
}

/**
 * 입력 이미지를 쓸 수 없다.
 *
 * [retryable] 의 뜻은 `ExternalApiException` · `FileStorageException` 과 같다 —
 * 사실일 뿐이고, 실제로 재시도할지는 부르는 쪽이 정한다.
 */
class ImageSourceUnavailableException(
    val retryable: Boolean,
    message: String,
) : RuntimeException(message)
