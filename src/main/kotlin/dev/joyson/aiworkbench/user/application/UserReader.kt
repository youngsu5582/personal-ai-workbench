package dev.joyson.aiworkbench.user.application

import dev.joyson.aiworkbench.user.UserView
import dev.joyson.aiworkbench.user.infrastructure.UserIdentityRepository
import dev.joyson.aiworkbench.user.infrastructure.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class UserReader(
    private val userRepository: UserRepository,
    private val userIdentityRepository: UserIdentityRepository,
) {
    @Transactional(readOnly = true)
    fun findByUuid(uuid: UUID): UserView? = userRepository.findByUuid(uuid)?.toView()

    @Transactional(readOnly = true)
    fun findByIdentity(issuer: String, subject: String): UserView? =
        userIdentityRepository.findWithUserByIssuerAndSubject(issuer, subject)?.user?.toView()

    @Transactional(readOnly = true)
    fun findIdentities(userId: Long): List<IdentityView> =
        userIdentityRepository.findByUserId(userId).map { it.toView() }

}