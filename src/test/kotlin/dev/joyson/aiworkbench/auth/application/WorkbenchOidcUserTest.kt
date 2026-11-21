package dev.joyson.aiworkbench.auth.application

import dev.joyson.aiworkbench.googleLogin
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkbenchOidcUserTest {

    @Test
    fun `이름은 Google 의 subject 가 아니라 워크벤치 사용자 UUID 다`() {
        val uuid = UUID.randomUUID()

        val user = googleLogin(uuid, googleSubject = "google-subject-1").principal as WorkbenchOidcUser

        assertEquals(uuid.toString(), user.name)
        assertEquals("google-subject-1", user.subject)
    }
}
