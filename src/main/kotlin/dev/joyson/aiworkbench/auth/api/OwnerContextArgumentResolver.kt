package dev.joyson.aiworkbench.auth.api

import dev.joyson.aiworkbench.auth.domain.ServiceKeyPrincipal
import dev.joyson.aiworkbench.ownership.AuthenticatedPrincipal
import dev.joyson.aiworkbench.ownership.OwnerContext
import dev.joyson.aiworkbench.user.UserRegistry
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.server.ResponseStatusException
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

    /** 컨트롤러가 [OwnerContext] 만 받는 경로는 계정 수준이라 서비스 키로 열지 않는다. */
    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): OwnerContext = resolveOwner(allowServiceKey = false)

    /**
     * 요청한 사용자를 확인한다. 서비스 키는 [allowServiceKey] 일 때만 받고, 그때 사용자는 서비스의 주인이다.
     *
     * 키는 서비스 하나의 대리인이다. 계정 수준 경로(서비스 · 키 관리, 내 정보, 전체 사용량)를 키로 열면
     * 한 서비스에 준 키로 다른 서비스를 만들거나 키를 더 발급할 수 있어 서비스 경계가 무너진다.
     * 새 경로도 [OwnerContext] 만 받으면 저절로 키를 막는다.
     */
    fun resolveOwner(allowServiceKey: Boolean): OwnerContext {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw InvalidBearerTokenException("인증되지 않은 요청이다")

        val uuid = when (val principal = authentication.principal) {
            is Jwt -> runCatching { UUID.fromString(principal.subject) }
                .getOrElse { throw InvalidBearerTokenException("토큰의 sub 가 올바르지 않다") }
            is ServiceKeyPrincipal ->
                if (allowServiceKey) principal.ownerUuid
                else throw ResponseStatusException(HttpStatus.FORBIDDEN, "서비스 키로는 계정 수준 요청을 할 수 없다")
            else -> throw InvalidBearerTokenException("Bearer 토큰이 필요하다")
        }

        val user = userRegistry.findByUuid(uuid)
            ?: throw InvalidBearerTokenException("존재하지 않는 사용자다")

        if (!user.active) {
            throw InvalidBearerTokenException("비활성화된 사용자다")
        }

        return OwnerContext.from(AuthenticatedPrincipal(userId = user.id, uuid = user.uuid))
    }
}
