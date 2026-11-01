package dev.joyson.aiworkbench.workspace

import java.util.UUID

/**
 * 사용자 등록에서 사용하는 서비스 모듈의 공개 경계다.
 * 기본 서비스는 사용자와 함께 생성되거나 함께 롤백되어야 하므로 호출자의 트랜잭션이 필요하다.
 */
interface WorkspaceRegistry {
    fun createDefault(ownerUuid: UUID)
}
