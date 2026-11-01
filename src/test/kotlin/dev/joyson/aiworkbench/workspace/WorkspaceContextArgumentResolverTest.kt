package dev.joyson.aiworkbench.workspace

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.auth.api.WorkspaceContextArgumentResolver
import dev.joyson.aiworkbench.ownership.WorkspaceContext
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import dev.joyson.aiworkbench.workspace.application.WorkspaceService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class WorkspaceContextArgumentResolverTest @Autowired constructor(
    private val users: UserRegistry,
    private val workspaces: WorkspaceRegistry,
    private val service: WorkspaceService,
    private val resolver: WorkspaceContextArgumentResolver,
) : IntegrationTest() {
    private fun user() = users.resolveOrRegister(
        RegisterIdentityCommand.of("https://accounts.google.com", UUID.randomUUID().toString(), "사용자", null),
    )

    private fun authenticate(uuid: UUID) {
        val token = Jwt.withTokenValue("test").header("alg", "none").subject(uuid.toString())
            .issuedAt(Instant.now()).build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(token)
    }

    private fun resolve(vararg values: String): WorkspaceContext {
        val request = MockHttpServletRequest()
        values.forEach { request.addHeader("X-Workspace-Id", it) }
        val method = javaClass.getDeclaredMethod("parameter", WorkspaceContext::class.java)
        return resolver.resolveArgument(MethodParameter(method, 0), null, ServletWebRequest(request), null)
    }

    @Suppress("unused")
    private fun parameter(context: WorkspaceContext) = context

    @AfterTest
    fun clearAuthentication() = SecurityContextHolder.clearContext()

    @Test
    fun `선택이 없으면 기본 서비스이고 소유한 서비스만 지정할 수 있다`() {
        val owner = user()
        val stranger = user()
        val own = service.create(owner.uuid, "내 서비스").uuid
        val other = service.create(stranger.uuid, "다른 서비스").uuid
        authenticate(owner.uuid)

        assertEquals(assertNotNull(workspaces.findDefaultUuid(owner.uuid)), resolve().workspaceUuid)
        assertEquals(own, resolve(own.toString()).workspaceUuid)
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { resolve(other.toString()) }.statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, assertFailsWith<ResponseStatusException> { resolve("bad") }.statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, assertFailsWith<ResponseStatusException> { resolve(own.toString(), other.toString()) }.statusCode)
    }
}
