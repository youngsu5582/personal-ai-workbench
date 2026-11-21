package dev.joyson.aiworkbench

import dev.joyson.aiworkbench.auth.application.WorkbenchOidcUser
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import java.time.Instant
import java.util.UUID

/**
 * Google 로그인을 마친 상태의 인증. 실제 Google 을 거치지 않고 로그인 뒤의 동작을 볼 때 쓴다.
 * Google 이 준 subject 와 워크벤치 사용자 UUID 를 일부러 다르게 둔다 — 어느 쪽이 밖으로 나가는지 가려야 한다.
 */
fun googleLogin(userUuid: UUID, googleSubject: String = "google-subject-1"): OAuth2AuthenticationToken {
    val authorities = listOf(SimpleGrantedAuthority("OIDC_USER"))
    val now = Instant.now()
    val idToken = OidcIdToken("id-token", now, now.plusSeconds(300), mapOf("sub" to googleSubject, "iss" to "https://accounts.google.com"))
    val principal = WorkbenchOidcUser(DefaultOidcUser(authorities, idToken), userId = 1L, userUuid = userUuid, workbenchDisplayName = "Joyson")
    return OAuth2AuthenticationToken(principal, authorities, "google")
}
