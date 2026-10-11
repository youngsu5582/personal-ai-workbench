package dev.joyson.aiworkbench.user.domain

import org.springframework.data.repository.Repository
import java.util.UUID

/** 구현은 Spring Data 가 만든다. */
interface UserRepository : Repository<User, Long> {

    fun save(user: User): User

    fun findById(id: Long): User?

    fun findByUuid(uuid: UUID): User?
}
