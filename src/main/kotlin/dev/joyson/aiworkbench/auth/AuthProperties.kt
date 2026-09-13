package dev.joyson.aiworkbench.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "workbench.auth")
data class AuthProperties(
    /** 내가 발급하는 토큰의 iss. Google 의 issuer 와 헷갈리면 안 된다 — 이건 내 서비스다. */
    val issuer: String = "https://workbench.local",
    /** 내 API 를 가리키는 audience. 다른 용도의 토큰이 흘러들어오는 걸 막는다. */
    val audience: String = "workbench-api",
    /** HS256 서명 키. 최소 32바이트. 검증자가 나 하나뿐이라 대칭키로 충분하다. */
    val jwtSecret: String,
    val accessTokenTtl: Duration = Duration.ofMinutes(15),
    /**
     * 로그인을 허용할 이메일. 비어 있으면 전체 허용(로컬 개발 전용).
     * 외부에 노출한다면 반드시 채운다 — 아니면 구글 계정 아무나 내 Provider 크레딧을 쓴다.
     */
    val allowedEmails: List<String> = emptyList(),
    /** 로그인 성공 후 토큰을 건네줄 때 쓰는 단기 HttpOnly 쿠키 이름. */
    val handoffCookieName: String = "wb_handoff",
    val handoffCookieTtl: Duration = Duration.ofSeconds(60),
    /** 운영에서는 반드시 true (HTTPS 전용 쿠키). */
    val cookieSecure: Boolean = false,
)
