package dev.joyson.aiworkbench.ownership

import java.util.UUID

/** 인증된 사용자가 접근할 수 있는 서비스의 범위다. */
data class WorkspaceContext(val owner: OwnerContext, val workspaceUuid: UUID)
