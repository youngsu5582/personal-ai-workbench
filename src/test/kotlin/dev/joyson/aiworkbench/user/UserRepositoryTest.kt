package dev.joyson.aiworkbench.user

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@DataJpaTest
class UserRepositoryTest @Autowired constructor(
    private val userRepository: UserRepository,
) {

    @Test
    fun `user can be stored and loaded by id`() {
        val user = User(
            displayName = "Joyson",
            externalSubject = "local:joyson",
        )

        val saved = userRepository.saveAndFlush(user)
        val loaded = userRepository.findById(saved.id).orElseThrow()

        assertEquals(saved.id, loaded.id)
        assertEquals("Joyson", loaded.displayName)
        assertEquals("local:joyson", loaded.externalSubject)
        assertEquals(UserStatus.ACTIVE, loaded.status)
        assertEquals(true, loaded.createdAt <= Instant.now())
    }
}
