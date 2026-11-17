package dev.joyson.aiworkbench

import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * 테스트가 쓸 인가 서버의 서명 키를 **실행할 때마다 새로 만든다.**
 *
 * 키 파일을 저장소에 두지 않으려는 것이다. 테스트 전용이라고 적어 두어도 개인키가 저장소에 보이면
 * 검사 도구가 경고하고, 누군가 그 키를 다른 곳에 쓴다.
 *
 * 시스템 프로퍼티로 넣는 이유는 [PostgresTestDatabase] 와 같다 — 어떤 컨텍스트든 같은 값을 본다.
 */
class TestSigningKey : LauncherSessionListener {
    override fun launcherSessionOpened(session: LauncherSession) {
        if (generated) return
        generated = true

        val privateKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        val pem = buildString {
            appendLine("-----BEGIN PRIVATE KEY-----")
            appendLine(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKey.encoded))
            appendLine("-----END PRIVATE KEY-----")
        }
        val file = Files.createTempFile("workbench-test-signing-key", ".pem")
        file.toFile().deleteOnExit()
        Files.writeString(file, pem)

        System.setProperty("authserver.signing-key", file.toUri().toString())
    }

    private companion object {
        @Volatile
        var generated = false
    }
}
