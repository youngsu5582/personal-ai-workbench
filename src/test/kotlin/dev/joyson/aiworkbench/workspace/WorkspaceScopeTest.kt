package dev.joyson.aiworkbench.workspace

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import dev.joyson.aiworkbench.workspace.application.WorkspaceService
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WorkspaceScopeTest @Autowired constructor(
    private val users: UserRegistry,
    private val workspaces: WorkspaceRegistry,
    private val service: WorkspaceService,
) : IntegrationTest() {
    private fun user() = users.resolveOrRegister(
        RegisterIdentityCommand.of("https://accounts.google.com", UUID.randomUUID().toString(), "사용자", null),
    )

    @Test
    fun `기본 서비스와 지정한 서비스는 소유자 범위에서만 찾는다`() {
        val owner = user()
        val stranger = user()
        val defaultUuid = assertNotNull(workspaces.findDefaultUuid(owner.uuid))
        val additionalUuid = service.create(owner.uuid, "추가 서비스").uuid

        assertEquals(defaultUuid, workspaces.findOwnedUuid(owner.uuid, defaultUuid))
        assertEquals(additionalUuid, workspaces.findOwnedUuid(owner.uuid, additionalUuid))
        assertNull(workspaces.findOwnedUuid(stranger.uuid, additionalUuid))
        assertNull(workspaces.findOwnedUuid(owner.uuid, UUID.randomUUID()))
        assertNull(workspaces.findDefaultUuid(UUID.randomUUID()))
    }
}
