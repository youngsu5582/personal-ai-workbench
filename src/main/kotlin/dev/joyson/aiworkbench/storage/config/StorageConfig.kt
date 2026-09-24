package dev.joyson.aiworkbench.storage.config

import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.FileStorageFactory
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import dev.joyson.aiworkbench.storage.PresignedUrlIssuerFactory
import dev.joyson.aiworkbench.storage.local.LocalFileStorage
import dev.joyson.aiworkbench.storage.local.LocalStorageProperties
import dev.joyson.aiworkbench.storage.s3.S3Clients
import dev.joyson.aiworkbench.storage.s3.S3FileStorage
import dev.joyson.aiworkbench.storage.s3.S3PresignedUrlIssuer
import dev.joyson.aiworkbench.storage.s3.S3StorageProperties
import dev.joyson.aiworkbench.storage.s3.S3StorageProperties.Companion.orNull
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

/**
 * 어느 보관소를 쓸지 **이름으로** 고른다.
 *
 * 구현마다 조건 애노테이션을 붙이던 방식을 걷어냈다. `@ConditionalOnMissingBean` 은 자동설정이
 * 아닌 일반 `@Configuration` 위에서 파싱 순서를 타고, 무엇보다 **빈이 반드시 하나여야 하는 상황**을
 * "없으면 이걸 쓴다" 로 표현하면 오타가 기동이 아니라 첫 저장에서 드러난다.
 *
 * `Map<String, FileStorageFactory>` 는 스프링이 **빈 이름을 키로** 채워준다. 이 저장소의
 * `ProviderRegistry` 가 이름으로 어댑터를 찾고 못 찾으면 가능한 목록과 함께 거절하는 것과 같은 모양이다.
 */
@Configuration
@EnableConfigurationProperties(
    StorageProperties::class,
    LocalStorageProperties::class,
    S3StorageProperties::class,
)
class StorageConfig {

    @Bean("local")
    fun localFileStorageFactory(properties: LocalStorageProperties) = FileStorageFactory {
        LocalFileStorage(Path.of(properties.root).toAbsolutePath().normalize())
    }

    @Bean("s3")
    fun s3FileStorageFactory(properties: S3StorageProperties) = FileStorageFactory {
        S3FileStorage(S3Clients.of(properties), requireBucket(properties))
    }

    private fun requireBucket(properties: S3StorageProperties): String =
        requireNotNull(properties.bucket.orNull()) { "workbench.storage.s3.bucket 이 필요하다" }

    @Bean
    fun s3PresignedUrlIssuerFactory(
        properties: S3StorageProperties,
        storageProperties: StorageProperties,
    ) = object : PresignedUrlIssuerFactory {
        override val provider = "s3"
        override fun create() = S3PresignedUrlIssuer(
            presigner = S3Clients.presigner(properties),
            bucket = requireBucket(properties),
            ttl = storageProperties.presignedUrlTtl,
        )
    }

    /**
     * 발급자는 **없을 수 있다.** 로컬 디스크는 주소에 서명할 수 없다.
     *
     * [fileStorage] 와 달리 못 고르는 것이 정상이므로 기동을 실패시키지 않고 `null` 을 준다.
     * `null` 을 돌려주면 스프링은 **NullBean 이라는 자리를 남긴다** — 주입은 `null` 로 되지만
     * "빈이 아예 없다" 는 아니다. 확인할 때는 타입이 아니라 **주입되는 값**을 봐야 한다.
     * 이름을 같은 [StorageProperties.provider] 로 찾으므로 **둘이 어긋날 수 없다** —
     * s3 를 고르면 둘 다 s3 고, local 을 고르면 보관소만 있고 발급자는 없다.
     */
    @Bean
    fun presignedUrlIssuer(
        factories: List<PresignedUrlIssuerFactory>,
        properties: StorageProperties,
    ): PresignedUrlIssuer? =
        factories.firstOrNull { it.provider == properties.provider }?.create()

    /**
     * 고르지 못하면 **기동을 실패시킨다.**
     *
     * 여기서 넘어가면 결과물을 어디에도 못 쓰는 채로 서비스가 뜨고, 그 사실은 첫 생성이
     * 끝난 뒤에야 드러난다. 오타와 빈 문자열이 가능한 이름과 함께 여기서 잡힌다.
     */
    @Bean
    fun fileStorage(
        factories: Map<String, FileStorageFactory>,
        properties: StorageProperties,
    ): FileStorage =
        factories[properties.provider]?.create()
            ?: throw IllegalStateException(
                "다루지 않는 보관소다: '${properties.provider}' (가능: ${factories.keys.sorted()})",
            )
}
