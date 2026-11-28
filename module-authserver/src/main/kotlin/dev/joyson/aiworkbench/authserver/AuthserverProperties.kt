package dev.joyson.aiworkbench.authserver

import org.springframework.boot.context.properties.ConfigurationProperties
import java.security.interfaces.RSAPrivateKey
import java.time.Duration

@ConfigurationProperties(prefix = "authserver")
data class AuthserverProperties(
    /**
     * 토큰에 서명하는 RSA 개인키.
     *
     * 설정에는 PEM(PKCS#8) 파일의 위치를 적는다(`file:` · `classpath:`). 그 파일을 키로 바꾸는 일은 Spring Security 가 한다.
     * 공개키는 이 키에서 꺼내 JWKS 로 내보낸다.
     */
    val signingKey: RSAPrivateKey,
    /** 접근 토큰의 `aud`. API 가 자기에게 온 토큰인지 이 값으로 가린다. */
    val audience: String,
    val accessTokenTtl: Duration,
    val console: Console,
) {
    /** 콘솔 백엔드가 인가 서버의 클라이언트로 등록되는 값. */
    data class Console(
        /** 콘솔 백엔드가 토큰 엔드포인트에 내는 비밀값. */
        val clientSecret: String,
        /** 인가 코드를 돌려보낼 주소. 인가 요청의 `redirect_uri` 가 이 값과 글자까지 같아야 한다. */
        val redirectUri: String,
    ) {
        init {
            // 환경변수를 빠뜨리면 자리표시자가 풀리지 않은 글자 그대로 비밀값이 된다. 길이로 막는다.
            require(clientSecret.length >= MIN_SECRET_LENGTH) {
                "authserver.console.client-secret 은 최소 ${MIN_SECRET_LENGTH}자여야 한다"
            }
        }

        private companion object {
            const val MIN_SECRET_LENGTH = 32
        }
    }
}
