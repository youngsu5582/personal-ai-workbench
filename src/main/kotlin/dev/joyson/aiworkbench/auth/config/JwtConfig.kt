package dev.joyson.aiworkbench.auth.config

import com.nimbusds.jose.jwk.source.ImmutableSecret
import dev.joyson.aiworkbench.auth.AuthProperties
import dev.joyson.aiworkbench.auth.domain.AuthErrorCode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import javax.crypto.spec.SecretKeySpec

/**
 * 내 토큰의 서명·검증 설정.
 *
 * HS256(대칭키)인 이유: 지금 이 토큰을 검증하는 주체가 나 하나뿐이다.
 * MCP 서버처럼 검증자가 내 프로세스 밖으로 나가는 날 RS256 + JWKS 로 바꾸면 되고,
 * 그때 바뀌는 건 이 파일 하나다.
 */
@Configuration
class JwtConfig(
    private val properties: AuthProperties,
) {
    private fun secretKey(): SecretKeySpec {
        val bytes = properties.jwtSecret.toByteArray()
        require(bytes.size >= 32) { "workbench.auth.jwt-secret 은 HS256 을 위해 최소 32바이트여야 한다" }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(secretKey()))

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder.withSecretKey(secretKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build()

        // 서명만 맞으면 통과시키지 않는다.
        // 내가 발급했고(iss) 내 API 를 향한(aud) 토큰인지까지 본다.
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(properties.issuer),
                OAuth2TokenValidator { jwt ->
                    if (checkContain(jwt)) {
                        OAuth2TokenValidatorResult.success()
                    } else {
                        OAuth2TokenValidatorResult.failure(
                            OAuth2Error(
                                AuthErrorCode.INVALID_AUDIENCE.code,
                                AuthErrorCode.INVALID_AUDIENCE.message,
                                null,
                            ),
                        )
                    }
                },
            ),
        )
        return decoder
    }

    private fun checkContain(jwt: Jwt): Boolean = jwt.audience?.contains(properties.audience) == true
}
