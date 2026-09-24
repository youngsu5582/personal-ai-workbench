package dev.joyson.aiworkbench.storage.local

import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.FileStorageException
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * 로컬 디스크에 파일로 둔다.
 *
 * 개발과 1대 운영에서 쓴다. 여러 대로 띄우는 순간 한 대가 쓴 파일을 다른 대가 못 읽으므로
 * 그때가 원격 보관소로 바꿀 시점이고, 바꾸는 일은 `workbench.storage.provider` 값 하나다 —
 * 코드는 건드리지 않는다.
 */
class LocalFileStorage(
    private val root: Path,
) : FileStorage {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun put(key: String, content: ByteArray, contentType: String) {
        val path = resolve(key)
        try {
            path.createParentDirectories()
            path.writeBytes(content)
        } catch (e: Exception) {
            // 디스크가 찼거나 권한이 없는 경우다. 다시 시도해도 대개 같지만
            // 일시적 잠금일 수도 있어 재시도 대상으로 둔다 — 상한은 부르는 쪽이 건다.
            throw FileStorageException(retryable = true, message = "파일을 쓰지 못했다: $key", cause = e)
        }
        // 키가 아니라 실제 경로를 남긴다. 키만으로는 root 를 모르면 파일을 열 수 없고,
        // root 를 아는 곳이 여기뿐이다.
        log.debug("결과물을 저장했다. path={} bytes={} type={}", path, content.size, contentType)
    }

    override fun read(key: String): ByteArray? =
        resolve(key).takeIf { it.exists() }?.readBytes()

    /**
     * 키를 실제 경로로 바꾼다.
     *
     * `..` 가 섞인 키는 [root] 바깥을 가리킬 수 있다. 키가 사용자 입력에서 파생될 여지가 있는 한
     * 이 검사는 선택이 아니다 — 정규화한 경로가 [root] 밑에 있는지 확인한다.
     */
    private fun resolve(key: String): Path {
        val resolved = root.resolve(key).normalize()
        if (!resolved.startsWith(root)) {
            throw FileStorageException(retryable = false, message = "보관소 밖을 가리키는 키다: $key")
        }
        return resolved
    }

    /**
     * root 를 미리 만든다. [put] 이 어차피 부모 디렉토리를 만들어서 **동작상으로는 중복이다.**
     *
     * 그럼에도 두는 이유는 둘이다 — 띄우자마자 결과물이 어디로 갈지 눈에 보이고,
     * "이 빈이 만들어졌다" 를 밖에서 관찰할 수 있는 유일한 지점이다.
     * `StorageConfigTest` 가 이것으로 "고르지 않은 구현은 아예 만들지 않는다" 를 확인한다.
     */
    init {
        Files.createDirectories(root)
    }
}
