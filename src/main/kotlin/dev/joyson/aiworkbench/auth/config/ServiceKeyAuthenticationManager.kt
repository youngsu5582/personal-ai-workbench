package dev.joyson.aiworkbench.auth.config

import dev.joyson.aiworkbench.auth.application.ServiceApiKeyService
import dev.joyson.aiworkbench.auth.domain.ServiceKeyPrincipal
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken

/**
 * Bearer 값이 서비스 키일 때 호출한 서비스를 확인한다.
 * 실패 사유(모르는 키 · 폐기 · 만료)를 나누어 알려 주지 않는다 — 키를 추측하는 쪽에 힌트가 된다.
 */
class ServiceKeyAuthenticationManager(private val keys: ServiceApiKeyService) : AuthenticationManager {
    override fun authenticate(authentication: Authentication): Authentication {
        val secret = (authentication as BearerTokenAuthenticationToken).token
        val principal = keys.authenticate(secret) ?: throw InvalidBearerTokenException("유효하지 않은 서비스 키다")
        return ServiceKeyAuthentication(principal)
    }
}

/** 확인된 서비스 키 호출. 원문은 들고 다니지 않는다 — 인증이 끝나면 쓸 데가 없고, 남기면 새어 나갈 길만 는다. */
class ServiceKeyAuthentication(private val principal: ServiceKeyPrincipal) : AbstractAuthenticationToken(emptyList()) {
    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): ServiceKeyPrincipal = principal

    override fun getCredentials(): Any? = null
}
