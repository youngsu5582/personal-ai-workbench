package dev.joyson.aiworkbench.authserver.config

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec

/**
 * 토큰에 서명하는 키와, 그 키에서 꺼낸 공개키.
 *
 * 기동할 때 한 번 확인한다. 서명에 쓸 수 없는 키면 첫 토큰 발급에서야 실패하기 때문이다.
 * `kid` 는 공개키의 지문(RFC 7638)이다. 키를 바꾸면 `kid` 도 바뀌어, 검증하는 쪽이 옛 공개키로 새 토큰을 보지 않는다.
 */
class SigningKey(privateKey: RSAPrivateKey) {

    val jwk: RSAKey

    init {
        // 공개키를 따로 받지 않는다. PKCS#8 로 저장한 RSA 개인키에는 공개 지수가 함께 들어 있다.
        require(privateKey is RSAPrivateCrtKey) { "$PROPERTY 에서 공개키를 꺼낼 수 없다. PKCS#8 형식의 RSA 개인키여야 한다" }
        val bits = privateKey.modulus.bitLength()
        require(bits >= MIN_BITS) { "$PROPERTY 는 최소 ${MIN_BITS}비트 RSA 키여야 한다 (지금 ${bits}비트)" }

        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)) as RSAPublicKey
        jwk = RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyIDFromThumbprint()
            .build()
    }

    private companion object {
        const val PROPERTY = "authserver.signing-key"
        const val MIN_BITS = 2048
    }
}
