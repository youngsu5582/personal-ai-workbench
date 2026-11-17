package dev.joyson.aiworkbench.authserver

import com.nimbusds.jose.JWSAlgorithm
import dev.joyson.aiworkbench.authserver.config.SigningKey
import org.junit.jupiter.api.assertThrows
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.spec.RSAPrivateKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SigningKeyTest {

    private fun rsa(bits: Int): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()

    @Test
    fun `개인키에서 공개키를 꺼내 RS256 서명 키를 만든다`() {
        val pair = rsa(2048)

        val jwk = SigningKey(pair.private as RSAPrivateKey).jwk

        assertEquals(pair.public, jwk.toRSAPublicKey())
        assertEquals(JWSAlgorithm.RS256, jwk.algorithm)
        assertFalse(jwk.keyID.isNullOrBlank())
        assertTrue(jwk.isPrivate)
    }

    @Test
    fun `같은 키는 언제나 같은 kid 를 갖고 다른 키는 다른 kid 를 갖는다`() {
        val key = rsa(2048).private as RSAPrivateKey

        assertEquals(SigningKey(key).jwk.keyID, SigningKey(key).jwk.keyID)
        assertFalse(SigningKey(key).jwk.keyID == SigningKey(rsa(2048).private as RSAPrivateKey).jwk.keyID)
    }

    @Test
    fun `2048비트보다 짧은 키는 거절한다`() {
        val short = rsa(1024).private as RSAPrivateKey

        assertThrows<IllegalArgumentException> { SigningKey(short) }
    }

    @Test
    fun `공개 지수가 없어 공개키를 꺼낼 수 없는 개인키는 거절한다`() {
        val full = rsa(2048).private as RSAPrivateKey
        val withoutPublicExponent = KeyFactory.getInstance("RSA")
            .generatePrivate(RSAPrivateKeySpec(full.modulus, full.privateExponent)) as RSAPrivateKey

        assertThrows<IllegalArgumentException> { SigningKey(withoutPublicExponent) }
    }
}
