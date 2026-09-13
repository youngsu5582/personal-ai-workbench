package dev.joyson.aiworkbench.auth.application

import dev.joyson.aiworkbench.auth.AuthProperties
import dev.joyson.aiworkbench.auth.domain.IssuedToken
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * 내 서비스의 access token 을 발급한다.
 *
 * 세션 방식과 달리 claim·수명·audience 를 전부 여기서 명시적으로 정한다. 그게 이 방식의 요점이다.
 * sub 에 내부 PK(Long) 가 아니라 uuid 를 넣는 이유:
 * 토큰은 외부로 나가는 물건이고, 내부 순차 PK 를 밖에 흘리지 않기 위해서다.
 * 대가는 요청마다 uuid → id 조회 1회인데, 그 덕분에 DISABLED 가 즉시 반영된다.
 */
@Service
class TokenService(
    private val jwtEncoder: JwtEncoder,
    private val properties: AuthProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun issueAccessToken(userUuid: UUID, displayName: String): IssuedToken {
        val now = Instant.now()
        val expiresAt = now.plus(properties.accessTokenTtl)
        // jti. 토큰 자체를 남기지 않고도 "어느 토큰인가" 를 추적하려고 둔다.
        val tokenId = UUID.randomUUID().toString()

        val claims = JwtClaimsSet.builder()
            .issuer(properties.issuer)
            .subject(userUuid.toString())
            .audience(listOf(properties.audience))
            .issuedAt(now)
            .expiresAt(expiresAt)
            .id(tokenId)
            .claim("name", displayName)
            .build()

        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue

        log.info("토큰 발행: sub={} jti={} exp={}", userUuid, tokenId, expiresAt)

        return IssuedToken(
            accessToken = token,
            expiresInSeconds = properties.accessTokenTtl.seconds,
        )
    }
}
