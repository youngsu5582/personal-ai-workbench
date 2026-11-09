package dev.joyson.aiworkbench.auth.api

import dev.joyson.aiworkbench.auth.domain.ServiceKeyPrincipal
import dev.joyson.aiworkbench.ownership.WorkspaceContext
import dev.joyson.aiworkbench.workspace.WorkspaceRegistry
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * 요청의 서비스는 사용자 소유권을 검증한 뒤에만 컨트롤러에 전달한다.
 * 서비스 키로 부르면 키의 서비스가 범위다. 헤더로 다른 서비스를 지정하면 거부한다(ADR 0004).
 */
@Component
class WorkspaceContextArgumentResolver(
    private val ownerResolver: OwnerContextArgumentResolver,
    private val workspaces: WorkspaceRegistry,
) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == WorkspaceContext::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): WorkspaceContext {
        val owner = ownerResolver.resolveOwner(allowServiceKey = true)
        val headers = webRequest.getHeaderValues("X-Workspace-Id")
        if (headers != null && headers.size != 1) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        val key = SecurityContextHolder.getContext().authentication?.principal as? ServiceKeyPrincipal
        val workspace = if (key != null) {
            // 같은 사용자의 다른 서비스라도 열지 않는다. 키를 받은 서비스가 자기 범위 밖을 보게 된다.
            if (headers != null && parse(headers.single()) != key.workspaceUuid) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "서비스 키의 서비스와 요청한 서비스가 다르다")
            }
            key.workspaceUuid
        } else if (headers == null) {
            workspaces.findDefaultUuid(owner.uuid)
                ?: throw IllegalStateException("사용자의 기본 서비스가 없다")
        } else {
            workspaces.findOwnedUuid(owner.uuid, parse(headers.single())) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return WorkspaceContext(owner, workspace)
    }

    private fun parse(header: String): UUID =
        runCatching { UUID.fromString(header) }.getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST) }
}
