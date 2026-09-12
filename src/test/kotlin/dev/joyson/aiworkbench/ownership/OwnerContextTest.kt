package dev.joyson.aiworkbench.ownership

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class OwnerContextTest {

    @Test
    fun `owner is resolved from authenticated principal`() {
        val userId = UUID.randomUUID()
        val principal = AuthenticatedPrincipal(userId = userId)

        val owner = OwnerContext.from(principal)

        assertEquals(userId, owner.userId)
    }
}
