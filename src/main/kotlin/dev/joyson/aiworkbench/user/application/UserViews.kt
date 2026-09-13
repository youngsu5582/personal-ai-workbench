package dev.joyson.aiworkbench.user.application

import dev.joyson.aiworkbench.user.UserView
import dev.joyson.aiworkbench.user.domain.User
import dev.joyson.aiworkbench.user.domain.UserIdentity

/**
 * 로그인 수단 하나의 표시용 표현.
 *
 * user 모듈 안에서만 쓰인다. 공개 경계([dev.joyson.aiworkbench.user.UserRegistry])에 두지 않는 이유는
 * 바깥 모듈이 이 타입을 필요로 하지 않기 때문이다.
 */
data class IdentityView(
    val issuer: String,
    val subject: String,
    val email: String?,
)

/**
 * Entity → 공개 View 매핑.
 *
 * 리포지토리에 의존하지 않는 **순수 함수**다. 그래서 읽기·쓰기 어느 쪽에서 써도
 * 숨은 쿼리가 따라붙지 않고, 스프링 없이 테스트할 수 있다.
 * 매핑 함수가 조회를 하기 시작하면 호출자가 비용을 볼 수 없게 된다.
 */
internal fun User.toView(): UserView = UserView(
    id = requireNotNull(id) { "영속화되지 않은 User 다" },
    uuid = uuid,
    displayName = displayName,
    active = active,
)

internal fun UserIdentity.toView(): IdentityView = IdentityView(
    issuer = issuer,
    subject = subject,
    email = email,
)
