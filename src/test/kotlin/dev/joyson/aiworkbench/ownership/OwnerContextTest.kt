package dev.joyson.aiworkbench.ownership

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class OwnerContextTest {

    @Test
    fun `owner is resolved from authenticated principal`() {
        val uuid = UUID.randomUUID()
        val principal = AuthenticatedPrincipal(userId = 42L, uuid = uuid)

        val owner = OwnerContext.from(principal)

        assertEquals(42L, owner.userId)
        assertEquals(uuid, owner.uuid)
    }
}
