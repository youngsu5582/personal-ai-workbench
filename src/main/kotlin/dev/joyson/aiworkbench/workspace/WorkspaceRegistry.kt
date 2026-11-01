package dev.joyson.aiworkbench.workspace

import java.util.UUID

/** 서비스 모듈의 공개 경계다. 다른 모듈은 서비스 엔티티 대신 이 포트로 소유권을 확인한다. */
interface WorkspaceRegistry {
    /** 사용자와 기본 서비스가 함께 생성되거나 롤백되도록 호출자의 트랜잭션이 필요하다. */
    fun createDefault(ownerUuid: UUID)

    /** 지정한 서비스가 사용자 소유면 그 UUID를, 아니면 null을 돌려준다. */
    fun findOwnedUuid(ownerUuid: UUID, workspaceUuid: UUID): UUID?

    /** 사용자의 기본 서비스가 없으면 null을 돌려준다. */
    fun findDefaultUuid(ownerUuid: UUID): UUID?

    /** 서비스가 없거나 다른 사용자의 서비스이면 false 다. */
    fun isOwnedBy(workspaceUuid: UUID, ownerUuid: UUID): Boolean
}
