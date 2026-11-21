package dev.joyson.aiworkbench.authserver

import org.springframework.boot.context.properties.ConfigurationProperties
import java.security.interfaces.RSAPrivateKey

@ConfigurationProperties(prefix = "authserver")
data class AuthserverProperties(
    /**
     * 토큰에 서명하는 RSA 개인키.
     *
     * 설정에는 PEM(PKCS#8) 파일의 위치를 적는다(`file:` · `classpath:`). 그 파일을 키로 바꾸는 일은 Spring Security 가 한다.
     * 공개키는 이 키에서 꺼내 JWKS 로 내보낸다.
     */
    val signingKey: RSAPrivateKey,
)
