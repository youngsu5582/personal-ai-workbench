package dev.joyson.aiworkbench.user.domain

import java.util.UUID

/** 구현은 infrastructure 의 `JpaUserRepository` 다. */
interface UserRepository {

    fun save(user: User): User

    fun findByIdOrNull(id: Long): User?

    fun findByUuid(uuid: UUID): User?
}
