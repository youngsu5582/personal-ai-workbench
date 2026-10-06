package dev.joyson.aiworkbench.user.domain

/** 구현은 infrastructure 의 `JpaUserIdentityRepository` 다. */
interface UserIdentityRepository {

    fun save(identity: UserIdentity): UserIdentity

    /** 지운 결과는 이 호출이 끝날 때 이미 DB 에 반영돼 있다. */
    fun remove(identity: UserIdentity)

    /** 로그인 경로의 핵심 조회. User 를 함께 가져온다. */
    fun findWithUserByIssuerAndSubject(issuer: String, subject: String): UserIdentity?

    fun findByUserId(userId: Long): List<UserIdentity>

    fun findByUserIdAndIssuer(userId: Long, issuer: String): UserIdentity?

    fun countByUserId(userId: Long): Long
}
