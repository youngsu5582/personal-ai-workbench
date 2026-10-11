package dev.joyson.aiworkbench.user.domain

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository

/** 구현은 Spring Data 가 만든다. */
interface UserIdentityRepository : Repository<UserIdentity, Long> {

    fun save(identity: UserIdentity): UserIdentity

    /**
     * 지운 결과는 이 호출이 끝날 때 이미 DB 에 반영돼 있다.
     *
     * 지우기를 쓰기 지연에 맡기지 않고 [flush] 로 바로 내보낸다.
     */
    fun remove(identity: UserIdentity) {
        delete(identity)
        flush()
    }

    /** 쓰기 지연에 맡긴다. 지운 결과가 바로 보여야 하면 [remove] 를 쓴다. */
    fun delete(identity: UserIdentity)

    fun flush()

    /**
     * 로그인 경로의 핵심 조회. User 를 함께 가져온다.
     *
     * `findByIssuerAndSubject(...).user` 로 쓰면 프록시 해석 때문에 쿼리가 조용히 한 번 더 나간다.
     * 어차피 항상 User 가 필요하므로 조인을 눈에 보이게 적는다.
     * 이름의 `WithUser` 는 Spring Data 가 무시하는 장식이라, 조인은 [Query] 가 한다.
     */
    @Query("select i from UserIdentity i join fetch i.user where i.issuer = :issuer and i.subject = :subject")
    fun findWithUserByIssuerAndSubject(issuer: String, subject: String): UserIdentity?

    fun findByUserId(userId: Long): List<UserIdentity>

    fun findByUserIdAndIssuer(userId: Long, issuer: String): UserIdentity?

    fun countByUserId(userId: Long): Long
}
