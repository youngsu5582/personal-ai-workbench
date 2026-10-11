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

    /**
     * [key] 의 [range] 바이트와 객체 전체 크기. 없으면 `null`.
     *
     * 내용 전체를 읽지 않고 일부만 볼 때 쓴다 — 형식은 앞부분의 매직넘버가 말한다.
     * 범위가 객체 끝을 넘으면 있는 만큼만 주고, 시작부터 넘으면 빈 바이트를 준다.
     */
    fun readRange(key: String, range: LongRange): PartialContent?

    /** [key] 를 지운다. 없어도 실패가 아니다. */
    fun delete(key: String)
}

/**
 * 객체의 일부와 전체 크기. HTTP 의 206 Partial Content 가 담는 것과 같다.
 *
 * 바이트 배열의 참조 비교를 피하려고 data class 로 두지 않는다.
 */
class PartialContent(
    val bytes: ByteArray,
    val totalSize: Long,
)

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
