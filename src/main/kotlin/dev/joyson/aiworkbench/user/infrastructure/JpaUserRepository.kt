package dev.joyson.aiworkbench.user.infrastructure

import dev.joyson.aiworkbench.user.domain.User
import dev.joyson.aiworkbench.user.domain.UserRepository
import org.springframework.data.jpa.repository.JpaRepository

/** [UserRepository] 의 구현. 실제 클래스는 Spring Data 가 만든다. */
interface JpaUserRepository : JpaRepository<User, Long>, UserRepository {

    /**
     * 포트가 `findById` 라는 이름을 쓰지 못하는 이유: 상속한 `findById` 가 `Optional` 을 돌려줘
     * 같은 이름으로 `User?` 를 선언하면 충돌한다.
     *
     * 몸체에서 `findByIdOrNull` 을 부르면 안 된다 — 멤버가 Spring Data 의 확장 함수를 가려 자기 자신을 부른다.
     */
    override fun findByIdOrNull(id: Long): User? = findById(id).orElse(null)
}
