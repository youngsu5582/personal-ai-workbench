package dev.joyson.aiworkbench.auth.domain

import java.util.UUID

/**
 * 서비스 키로 확인된 호출자다. 사람이 아니라 서비스 하나의 대리인이라, 범위는 [workspaceUuid] 하나로 고정된다.
 * 소유자는 서비스의 주인이며 비활성 사용자를 막을 때 쓴다.
 */
data class ServiceKeyPrincipal(
    val keyUuid: UUID,
    val workspaceUuid: UUID,
    val ownerUuid: UUID,
)
