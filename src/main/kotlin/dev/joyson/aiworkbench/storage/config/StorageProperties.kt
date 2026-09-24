package dev.joyson.aiworkbench.storage.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

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
    /**
     * 발급한 주소의 수명.
     *
     * 서명이 인증을 대신하므로 **유출된 주소의 유효 기간이 곧 피해 범위**다. 짧을수록 안전하지만,
     * 사용자가 목록을 열어두고 한참 뒤에 클릭하면 만료된 주소를 누르게 된다.
     * 5분은 "보고 나서 누른다" 를 덮으면서 유출 창을 좁게 두는 값이다.
     */
    val presignedUrlTtl: Duration = Duration.ofMinutes(5),
)
