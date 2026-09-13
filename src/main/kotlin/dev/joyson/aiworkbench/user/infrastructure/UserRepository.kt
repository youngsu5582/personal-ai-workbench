package dev.joyson.aiworkbench.user.infrastructure

import dev.joyson.aiworkbench.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, Long> {
    fun findByUuid(uuid: UUID): User?
}
