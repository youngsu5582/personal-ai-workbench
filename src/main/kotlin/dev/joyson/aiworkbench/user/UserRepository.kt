package dev.joyson.aiworkbench.user

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, UUID> {
    fun findByExternalSubject(externalSubject: String): User?
}
