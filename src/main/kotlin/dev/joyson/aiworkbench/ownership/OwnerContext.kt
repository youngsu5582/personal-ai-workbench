package dev.joyson.aiworkbench.ownership

import java.util.UUID

data class OwnerContext(
    val userId: UUID,
) {
    companion object {
        fun from(principal: AuthenticatedPrincipal): OwnerContext =
            OwnerContext(userId = principal.userId)
    }
}
