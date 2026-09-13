package dev.joyson.aiworkbench.user.infrastructure

import dev.joyson.aiworkbench.user.domain.UserIdentity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserIdentityRepository : JpaRepository<UserIdentity, Long> {

    /**
     * 로그인 경로의 핵심 조회. User 를 함께 가져온다.
     *
     * `findByIssuerAndSubject(...).user` 로 쓰면 프록시 해석 때문에 쿼리가 조용히 한 번 더 나간다.
     * 어차피 항상 User 가 필요하므로 조인을 눈에 보이게 적는다.
     */
    @Query("select i from UserIdentity i join fetch i.user where i.issuer = :issuer and i.subject = :subject")
    fun findWithUserByIssuerAndSubject(issuer: String, subject: String): UserIdentity?

    fun findByUserId(userId: Long): List<UserIdentity>

    fun findByUserIdAndIssuer(userId: Long, issuer: String): UserIdentity?

    fun countByUserId(userId: Long): Long
}
