package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.option.FileSource
import org.springframework.stereotype.Repository
import java.util.UUID
import java.net.URI

/**
 * 참조가 가리키는 파일의 자리를 찾는다.
 *
 * **소유자와 함께** 찾는다 — 남의 파일은 없는 것과 구분되지 않아야 한다.
 * 다르게 답하면 "그 uuid 는 존재한다" 가 새어 나간다.
 *
 * **바이트는 읽지 않는다.** 접수는 트랜잭션 안이라 보관소 왕복을 넣으면 안 되고,
 * 존재를 확인하는 데는 메타 행이면 충분하다.
 *
 * 찾지 못한 것은 예외가 아니라 `null` 이다. 접수는 400 을 만들고 워커는 Task 를 닫는데,
 * 그렇게 호출 지점마다 처리가 갈리기 때문이다.
 */
@Repository
class FileSourceFinder(
    private val generatedFileFinder: GeneratedFileFinder,
) {

    fun findOwned(source: FileSource, ownerUuid: UUID): FileLocation? = when (source) {
        // sealed 라 참조의 종류가 늘면 여기가 컴파일 에러로 드러난다.
        is FileSource.Generated -> generatedFileFinder.findOwned(source.uuid, ownerUuid)
    }
}

/**
 * 실행 시점에 준비한 입력 파일의 바이트 또는 읽기 주소.
 *
 * 포트의 `ExternalApiFileInput` 과 모양이 같지만 여기서 그것을 만들지 않는다 —
 * 두 어휘를 잇는 책임은 [ProviderRequestFactory] 한 곳에 있고, 포트를 아는 자리가 둘이 되면
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
