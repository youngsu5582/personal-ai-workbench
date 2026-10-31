package dev.joyson.aiworkbench.workspace

import java.util.UUID

/** 사용자 등록과 같은 트랜잭션에서 기본 작업 공간을 만든다. */
interface WorkspaceRegistry {
    fun createDefault(ownerUuid: UUID)
}
