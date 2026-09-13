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
)
