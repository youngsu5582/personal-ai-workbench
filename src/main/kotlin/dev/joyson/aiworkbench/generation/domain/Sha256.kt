package dev.joyson.aiworkbench.generation.domain

import java.security.MessageDigest

/**
 * 바이트의 SHA-256 요약.
 *
 * 문자열이 아니라 타입으로 감싸는 이유는, 보관소 키를 만드는 곳이 **아무 문자열이나 받지 않게**
 * 하기 위해서다. 자리표시자나 파일명이 키가 되는 길을 컴파일 단계에서 막는다.
 */
@JvmInline
value class Sha256 private constructor(val hex: String) {

    override fun toString(): String = hex

    companion object {
        private const val ALGORITHM = "SHA-256"
        private const val HEX_LENGTH = 64
        private val HEX = Regex("[0-9a-f]{$HEX_LENGTH}")

        /**
         * 바이트에서 계산한다.
         *
         * `MessageDigest` 인스턴스를 재사용하지 않는다 — **스레드 안전하지 않다.**
         * 이 함수를 부르는 워커는 싱글톤 빈이고 여러 Task 를 동시에 처리한다.
         */
        fun of(content: ByteArray): Sha256 =
            Sha256(MessageDigest.getInstance(ALGORITHM).digest(content).toHex())

        /** 이미 계산된 값을 형식만 검증해 받는다. `of` 가 계산해서 만드는 문이라면 이쪽은 밖에서 들어오는 문이다. */
        fun parse(hex: String): Sha256 {
            require(HEX.matches(hex)) { "SHA-256 은 소문자 hex $HEX_LENGTH 자여야 한다: $hex" }
            return Sha256(hex)
        }

        private fun ByteArray.toHex(): String =
            joinToString("") { "%02x".format(it) }
    }
}
