package dev.joyson.aiworkbench.user.infrastructure

import dev.joyson.aiworkbench.user.domain.UserIdentity
import dev.joyson.aiworkbench.user.domain.UserIdentityRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/** [UserIdentityRepository] 의 구현. 실제 클래스는 Spring Data 가 만든다. */
interface JpaUserIdentityRepository : JpaRepository<UserIdentity, Long>, UserIdentityRepository {

    /**
     * 포트가 `delete` 가 아닌 이름을 쓰는 이유: 같은 이름이면 몸체의 `delete` 가 자기 자신을 부른다.
     *
     * 지우기를 쓰기 지연에 맡기지 않고 바로 내보낸다. 포트가 "끝날 때 반영돼 있다" 를 약속하기 때문이다.
     */
    override fun remove(identity: UserIdentity) {
        delete(identity)
        flush()
    }

    /**
     * `findByIssuerAndSubject(...).user` 로 쓰면 프록시 해석 때문에 쿼리가 조용히 한 번 더 나간다.
     * 어차피 항상 User 가 필요하므로 조인을 눈에 보이게 적는다.
     */
    @Query("select i from UserIdentity i join fetch i.user where i.issuer = :issuer and i.subject = :subject")
    override fun findWithUserByIssuerAndSubject(issuer: String, subject: String): UserIdentity?
}
