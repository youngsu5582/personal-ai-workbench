package dev.joyson.aiworkbench.ownership

import java.util.UUID

/**
 * 인증이 끝난 뒤 확정된 요청 주체다.
 *
 * 어떤 방식으로 인증했는지(세션/JWT), 어느 provider 로 로그인했는지는 여기 들어오지 않는다.
 * 그래서 인증 방식이 바뀌어도 이 타입과 [OwnerContext] 는 바뀌지 않는다.
 */
data class AuthenticatedPrincipal(
    /** 내부 DB 식별자. 소유권 조회·FK 는 전부 이 값을 쓴다. */
    val userId: Long,
    /** 외부 노출 식별자. 로그와 응답에 쓴다. */
    val uuid: UUID,
)
