package dev.joyson.aiworkbench.user.api

import dev.joyson.aiworkbench.ownership.OwnerContext
import dev.joyson.aiworkbench.user.application.UserReader
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 현재 사용자의 프로필.
 *
 * auth 가 아니라 user 모듈에 있는 이유: 이 응답의 내용물이 전부 user 의 데이터다.
 * auth 적인 요소는 "지금 누구인가" 하나뿐이고 그건 [OwnerContext] 로 받는다.
 * 덕분에 이 컨트롤러는 어떤 방식으로 인증했는지(세션·JWT), 어느 provider 로 로그인했는지 모른다.
 */
@RestController
class MeController(
    private val userReader: UserReader,
) {
    @GetMapping("/api/me")
    fun me(owner: OwnerContext): ResponseEntity<MeResponse> {
        val user = userReader.findByUuid(owner.uuid)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            MeResponse(
                uuid = user.uuid.toString(),
                displayName = user.displayName,
                active = user.active,
                // 로그인 수단 목록은 이 화면만 필요로 한다. 그래서 UserView 에 끼워 넣지 않고 따로 부른다.
                identities = userReader.findIdentities(user.id).map {
                    IdentitySummary(issuer = it.issuer, subject = it.subject, email = it.email)
                },
            ),
        )
    }
}

data class MeResponse(
    val uuid: String,
    val displayName: String,
    val active: Boolean,
    val identities: List<IdentitySummary>,
)

data class IdentitySummary(
    val issuer: String,
    val subject: String,
    val email: String?,
)
