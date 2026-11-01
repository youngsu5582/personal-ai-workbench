package dev.joyson.aiworkbench.workspace

import java.util.UUID

/** 사용자 등록과 서비스 범위 확인에 사용하는 공개 경계다. */
interface WorkspaceRegistry {
    /** 사용자와 기본 서비스가 함께 생성되거나 롤백되도록 호출자의 트랜잭션이 필요하다. */
    fun createDefault(ownerUuid: UUID)

    /** 지정한 서비스가 사용자 소유면 그 UUID를, 아니면 null을 돌려준다. */
    fun findOwnedUuid(ownerUuid: UUID, workspaceUuid: UUID): UUID?

    /** 사용자의 기본 서비스가 없으면 null을 돌려준다. */
    fun findDefaultUuid(ownerUuid: UUID): UUID?

    fun isOwnedBy(workspaceUuid: UUID, ownerUuid: UUID): Boolean
}
