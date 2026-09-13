package dev.joyson.aiworkbench.ownership

import java.util.UUID

/**
 * 현재 요청이 어떤 사용자의 리소스를 다루는지 나타낸다.
 *
 * 클라이언트가 보낸 owner id 는 신뢰하지 않는다. 항상 인증 주체에서만 파생된다.
 */
data class OwnerContext(
    val userId: Long,
    val uuid: UUID,
) {
    companion object {
        fun from(principal: AuthenticatedPrincipal): OwnerContext =
            OwnerContext(userId = principal.userId, uuid = principal.uuid)
    }
}
