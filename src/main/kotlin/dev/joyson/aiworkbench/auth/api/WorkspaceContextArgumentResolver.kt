package dev.joyson.aiworkbench.auth.api

import dev.joyson.aiworkbench.ownership.WorkspaceContext
import dev.joyson.aiworkbench.workspace.WorkspaceRegistry
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/** 요청의 서비스는 사용자 소유권을 검증한 뒤에만 컨트롤러에 전달한다. */
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
        val owner = ownerResolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory)
        val headers = webRequest.getHeaderValues("X-Workspace-Id")
        if (headers != null && headers.size != 1) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        val workspace = if (headers == null) {
            workspaces.findDefaultUuid(owner.uuid)
                ?: throw IllegalStateException("사용자의 기본 서비스가 없다")
        } else {
            val requested = runCatching { UUID.fromString(headers.single()) }
                .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST) }
            workspaces.findOwnedUuid(owner.uuid, requested) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return WorkspaceContext(owner, workspace)
    }
}
