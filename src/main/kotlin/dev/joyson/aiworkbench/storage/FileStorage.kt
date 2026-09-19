package dev.joyson.aiworkbench.storage

/**
 * 키로 바이트를 넣고 빼는 곳.
 *
 * 구현이 S3 든 R2 든 로컬 디스크든, 쓰는 쪽은 이 인터페이스만 안다.
 * 담긴 바이트가 무엇인지는 모른다 — 키의 의미도 부르는 쪽에 있다.
 *
 * 바이트를 통째로 주고받는다. 이미지 한 장이 수 MB 수준이라 지금은 감당되고,
 * 영상으로 넘어가면 여기가 먼저 막힌다.
 */
interface FileStorage {

    /**
     * [key] 자리에 [content] 를 쓴다. 같은 키가 이미 있으면 덮어쓴다.
     *
     * 덮어쓰기는 키-값 보관소의 성질을 그대로 따른 것이다. 부르는 쪽이 키를 겹치지 않게
     * 만들면 일어나지 않는 일이라, 여기서 막지 않는다.
     */
    fun put(key: String, content: ByteArray, contentType: String)

    /** [key] 의 내용. 없으면 `null`. */
    fun read(key: String): ByteArray?
}

/**
 * 보관 작업 실패.
 *
 * 구현체별 예외(`S3Exception`, `IOException`)가 경계 밖으로 새지 않게 여기서 한 종류로 바꾼다.
 * [retryable] 의 뜻은 [dev.joyson.aiworkbench.provider.ExternalApiException] 과 같다 —
 * 사실일 뿐, 실제로 재시도할지는 부르는 쪽이 정한다.
 */
class FileStorageException(
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
