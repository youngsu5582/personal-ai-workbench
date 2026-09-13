package dev.joyson.aiworkbench.auth.application

import dev.joyson.aiworkbench.auth.AuthProperties
import dev.joyson.aiworkbench.auth.domain.AuthErrorCode
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service

/**
 * Google 로그인 콜백에서 "우리 쪽 User" 를 확정하는 지점이다.
 *
 * 여기서 하는 일은 4가지뿐이고, 나머지(code 교환·ID token 서명 검증·userinfo 조회)는 전부 Spring 이 한다.
 *   1. 이메일 검증 여부와 allowlist 확인
 *   2. (issuer, subject) 정규화
 *   3. UserRegistry 로 조회 또는 신규 등록
 *   4. 비활성 사용자 차단
 */
@Service
class OidcUserService(
    private val userRegistry: UserRegistry,
    private val properties: AuthProperties,
) : OAuth2UserService<OidcUserRequest, OidcUser> {

    private val log = LoggerFactory.getLogger(javaClass)
    private val delegate = OidcUserService()

    @PostConstruct
    fun init() {
        log.info("Initializing OidcUserService. token-ttl: {}", properties.accessTokenTtl)
    }

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = delegate.loadUser(userRequest)

        val rawIssuer = oidcUser.issuer?.toString()
            ?: userRequest.clientRegistration.providerDetails.issuerUri
            ?: throw authError(AuthErrorCode.INVALID_ISSUER)

        val subject = oidcUser.subject
            ?: throw authError(AuthErrorCode.INVALID_SUBJECT)
        val email = oidcUser.email?.lowercase()
        assertAllowed(email, oidcUser.emailVerified ?: false)

        val command = RegisterIdentityCommand.of(
            issuer = rawIssuer,
            subject = subject,
            displayName = oidcUser.fullName ?: oidcUser.preferredUsername ?: email ?: subject,
            email = email,
        )

        val user = userRegistry.resolveOrRegister(command)
        if (!user.active) {
            throw authError(AuthErrorCode.USER_DISABLED)
        }

        log.info("로그인 성공: uuid={} issuer={}", user.uuid, command.issuer)
        return WorkbenchOidcUser(oidcUser, user.id, user.uuid, user.displayName)
    }

    private fun assertAllowed(email: String?, emailVerified: Boolean) {
        if (properties.allowedEmails.isEmpty()) return

        if (email == null || !emailVerified) {
            throw authError(AuthErrorCode.EMAIL_NOT_VERIFIED)
        }
        if (properties.allowedEmails.none { it.trim().lowercase() == email }) {
            log.warn("allowlist 밖의 이메일로 로그인 시도가 있었다: {}", email)
            throw authError(AuthErrorCode.EMAIL_NOT_ALLOWED)
        }
    }

    private fun authError(error: AuthErrorCode) =
        OAuth2AuthenticationException(OAuth2Error(error.code, error.message, null), error.message)
}
