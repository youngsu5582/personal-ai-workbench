package dev.joyson.aiworkbench.storage.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "workbench.storage")
data class StorageProperties(
    /**
     * 쓸 보관소의 이름. 등록된 [dev.joyson.aiworkbench.storage.FileStorageFactory] 의 빈 이름과 맞춰야 한다.
     *
     * enum 이 아니라 `String` 인 이유는 **선택 지점을 고치지 않고 구현을 늘리기** 위해서다.
     * enum 으로 두면 `minio` 를 이름만 갈라 등록하고 싶을 때도 enum 과 `when` 을 같이 고쳐야 한다.
     * 오타나 빈 문자열은 기동 시점에 가능한 이름 목록과 함께 잡힌다.
     */
    val provider: String = "local",
)
