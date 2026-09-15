package dev.joyson.aiworkbench.storage.local

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "workbench.storage.local")
data class LocalStorageProperties(
    /** 결과물을 둘 디렉토리. 없으면 만든다. */
    val root: String = "./var/assets",
)
