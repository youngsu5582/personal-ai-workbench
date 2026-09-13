package dev.joyson.aiworkbench.auth.api

import dev.joyson.aiworkbench.ownership.AuthenticatedPrincipal
import dev.joyson.aiworkbench.ownership.OwnerContext
import dev.joyson.aiworkbench.user.UserRegistry
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

/**
 * 컨트롤러가 [OwnerContext] 를 파라미터로 바로 받게 해준다.
 *
 * 이 해석기가 `auth` 에 있는 이유: `ownership` 을 프레임워크와 무관한 순수 규칙으로 남기기 위해서다.
 * 덕분에 OwnerContextTest 는 여전히 Spring 없이 돈다.
 *
 * 토큰의 sub(uuid) → 내부 id 조회를 매 요청 한다. 그 대가로 DISABLED 처리가 즉시 반영된다.
 * (트래픽이 늘면 이 지점이 캐시 후보다.)
 */
@Component
class OwnerContextArgumentResolver(
    private val userRegistry: UserRegistry,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        OwnerContext::class.java == parameter.parameterType

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): OwnerContext {

        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw InvalidBearerTokenException("인증되지 않은 요청이다")

        val jwt = authentication.principal as? Jwt
            ?: throw InvalidBearerTokenException("Bearer 토큰이 필요하다")

        val uuid = runCatching { UUID.fromString(jwt.subject) }
            .getOrElse { throw InvalidBearerTokenException("토큰의 sub 가 올바르지 않다") }

        val user = userRegistry.findByUuid(uuid)
            ?: throw InvalidBearerTokenException("존재하지 않는 사용자다")

        if (!user.active) {
            throw InvalidBearerTokenException("비활성화된 사용자다")
        }

        return OwnerContext.from(AuthenticatedPrincipal(userId = user.id, uuid = user.uuid))
    }
}
