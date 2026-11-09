package dev.joyson.aiworkbench.auth.application

import dev.joyson.aiworkbench.auth.domain.ServiceApiKey
import dev.joyson.aiworkbench.auth.domain.ServiceApiKeyRepository
import dev.joyson.aiworkbench.auth.domain.ServiceKeyPrincipal
import dev.joyson.aiworkbench.user.UserRegistry
import dev.joyson.aiworkbench.workspace.WorkspaceRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

/** 로그인한 사용자가 소유한 서비스의 키를 관리하고, 키로 들어온 호출을 확인한다. */
@Service
class ServiceApiKeyService(
    private val repository: ServiceApiKeyRepository,
    private val workspaces: WorkspaceRegistry,
    private val users: UserRegistry,
) {
    private val random = SecureRandom()

    /**
     * 키를 발급하고 원문을 한 번 반환한다. 소유하지 않은 서비스면 null 이다.
     * 32바이트 난수로 만든 키라 해시를 빠르게 조회해도 사람이 정한 비밀번호와 달리 추측하기 어렵다.
     * 이름과 만료 시각은 호출 전에 검증해야 한다.
     */
    @Transactional
    fun issue(owner: UUID, workspace: UUID, name: String, expiresAt: Instant?): IssuedServiceApiKey? {
        if (!workspaces.isOwnedBy(workspace, owner)) return null
        val bytes = ByteArray(32).also(random::nextBytes)
        val secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val key = repository.save(
            ServiceApiKey(workspaceUuid = workspace, name = name, keyHash = hash(secret), keyHint = secret.take(11), expiresAt = expiresAt),
        )
        return IssuedServiceApiKey(key, secret)
    }

    /**
     * 키 원문으로 호출한 서비스를 확인한다. 모르는 키 · 폐기 · 만료 · 서비스가 사라진 키와 주인이 비활성인 키는 null 이다.
     * 원문 전체의 해시로 찾는다 — 표시용 힌트는 유일하지 않아 인증에 쓰지 않는다.
     *
     * 주인의 활성 상태를 인증 단계에서 본다. 모델 목록처럼 소유자를 해석하지 않는 경로가 있어,
     * 요청의 소유자 해석에만 맡기면 비활성 사용자의 키가 그런 경로를 통과한다.
     */
    @Transactional(readOnly = true)
    fun authenticate(secret: String, now: Instant = Instant.now()): ServiceKeyPrincipal? {
        val key = repository.findByKeyHash(hash(secret))?.takeIf { it.usableAt(now) } ?: return null
        val owner = workspaces.findOwnerUuid(key.workspaceUuid) ?: return null
        if (users.findByUuid(owner)?.active != true) return null
        return ServiceKeyPrincipal(keyUuid = key.uuid, workspaceUuid = key.workspaceUuid, ownerUuid = owner)
    }

    private fun hash(secret: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)))

    companion object {
        /** 발급하는 키의 머리말. 같은 `Authorization: Bearer` 헤더에서 로그인 토큰(JWT)과 키를 이것으로 가른다. */
        const val PREFIX = "pawb_"
    }

    /** 소유한 서비스의 키를 폐기된 키까지 반환한다. 서비스가 없거나 소유하지 않으면 null 이다. */
    @Transactional(readOnly = true)
    fun list(owner: UUID, workspace: UUID): List<ServiceApiKey>? =
        if (workspaces.isOwnedBy(workspace, owner)) repository.findAllByWorkspaceUuidOrderByCreatedAtAsc(workspace) else null

    /** 키가 해당 서비스에 있으면 폐기하고 true, 서비스나 키를 찾지 못하면 false 를 반환한다. */
    @Transactional
    fun revoke(owner: UUID, workspace: UUID, keyUuid: UUID): Boolean {
        if (!workspaces.isOwnedBy(workspace, owner)) return false
        val key = repository.findByUuidAndWorkspaceUuid(keyUuid, workspace) ?: return false
        key.revoke(Instant.now())
        return true
    }
}

/** 원문이 로그에 실리지 않도록 내용을 출력하는 toString 을 생성하지 않는다. */
class IssuedServiceApiKey(val key: ServiceApiKey, val secret: String)
