package dev.joyson.aiworkbench.auth.application

import dev.joyson.aiworkbench.auth.domain.ServiceApiKey
import dev.joyson.aiworkbench.auth.domain.ServiceApiKeyRepository
import dev.joyson.aiworkbench.workspace.WorkspaceRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

@Service
class ServiceApiKeyService(
    private val repository: ServiceApiKeyRepository,
    private val workspaces: WorkspaceRegistry,
) {
    private val random = SecureRandom()

    @Transactional
    fun issue(owner: UUID, workspace: UUID, name: String, expiresAt: Instant?): IssuedServiceApiKey? {
        if (!workspaces.isOwnedBy(workspace, owner)) return null
        val bytes = ByteArray(32).also(random::nextBytes)
        val secret = "pawb_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)))
        val key = repository.save(
            ServiceApiKey(workspaceUuid = workspace, name = name, keyHash = hash, keyHint = secret.take(11), expiresAt = expiresAt),
        )
        return IssuedServiceApiKey(key, secret)
    }

    @Transactional(readOnly = true)
    fun list(owner: UUID, workspace: UUID): List<ServiceApiKey>? =
        if (workspaces.isOwnedBy(workspace, owner)) repository.findAllByWorkspaceUuidOrderByCreatedAtAsc(workspace) else null

    @Transactional
    fun revoke(owner: UUID, workspace: UUID, keyUuid: UUID): Boolean {
        if (!workspaces.isOwnedBy(workspace, owner)) return false
        val key = repository.findByUuidAndWorkspaceUuid(keyUuid, workspace) ?: return false
        key.revoke(Instant.now())
        return true
    }
}

// 원문이 로그에 실리지 않도록 toString 을 생성하지 않는다.
class IssuedServiceApiKey(val key: ServiceApiKey, val secret: String)
