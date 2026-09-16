package dev.joyson.aiworkbench.storage.config

import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.local.LocalFileStorage
import dev.joyson.aiworkbench.storage.local.LocalStorageProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

@Configuration
@EnableConfigurationProperties(LocalStorageProperties::class)
class StorageConfig {

    /**
     * 원격 보관소가 설정돼 있으면 그쪽이 등록되고, 아무것도 없으면 로컬이 받친다.
     * 기본값이 있으니 "설정 안 해서 결과물을 버리는" 상태가 생기지 않는다.
     */
    @Bean
    @ConditionalOnMissingBean(FileStorage::class)
    fun localFileStorage(properties: LocalStorageProperties): FileStorage =
        LocalFileStorage(Path.of(properties.root).toAbsolutePath().normalize())
}
