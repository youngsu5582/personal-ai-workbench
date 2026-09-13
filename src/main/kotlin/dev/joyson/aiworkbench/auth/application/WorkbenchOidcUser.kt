package dev.joyson.aiworkbench.auth.application

import org.springframework.security.oauth2.core.oidc.user.OidcUser
import java.util.UUID

/**
 * Google 이 준 OidcUser 에 "우리 쪽 User" 를 얹은 것.
 *
 * 로그인 성공 핸들러가 토큰을 발급할 때 DB 를 다시 조회하지 않게 해준다.
 * Kotlin 의 인터페이스 위임(`by`)으로 OidcUser 의 나머지 구현은 그대로 넘긴다.
 *
 * domain 이 아니라 application 에 있는 이유: 이건 도메인 개념이 아니라
 * Spring Security 의 principal 타입을 감싼 것이다. domain 은 프레임워크를 몰라야 한다.
 */
class WorkbenchOidcUser(
    private val delegate: OidcUser,
    val userId: Long,
    val userUuid: UUID,
    val workbenchDisplayName: String,
) : OidcUser by delegate
