package dev.joyson.aiworkbench.storage

/**
 * [FileStorage] 를 **필요해진 순간에** 만든다.
 *
 * 구현체를 빈으로 바로 등록하지 않는 이유는, 등록하면 **쓰지 않을 구현도 세워지기 때문**이다.
 * `LocalFileStorage` 는 생성 시점에 디렉토리를 만들고 S3 쪽은 클라이언트를 붙든다 —
 * s3 를 고른 환경에서 빈 `var/assets` 가 생기는 것은 설정이 거짓말을 하는 것이다.
 *
 * 빈 **이름**이 곧 보관소 이름이다. 구현을 늘리는 일이 `@Bean("이름")` 한 줄이 되고,
 * 고르는 코드는 그대로다.
 */
fun interface FileStorageFactory {
    fun create(): FileStorage
}
