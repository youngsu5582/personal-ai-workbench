package dev.joyson.aiworkbench.auth

import com.jayway.jsonpath.JsonPath
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.Test

/** 내 다른 서비스(블로그 서버 등)가 로그인 없이 서비스 키로 워크벤치를 부르는 경로. */
class ServiceKeyAuthenticationApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val users: UserRegistry,
    private val tokens: TokenService,
    private val jdbc: JdbcTemplate,
    private val em: EntityManager,
) : IntegrationTest() {
    private class Owner(val uuid: UUID, val token: String)

    private fun owner(): Owner {
        val user = users.resolveOrRegister(RegisterIdentityCommand.of("https://accounts.google.com", UUID.randomUUID().toString(), "사용자", null))
        return Owner(user.uuid, tokens.issueAccessToken(user.uuid, user.displayName).accessToken)
    }

    private fun defaultService(owner: Owner): String = JsonPath.read(
        mockMvc.get("/api/services") { header("Authorization", "Bearer ${owner.token}") }.andReturn().response.contentAsString,
        "$[0].uuid",
    )

    private fun newService(owner: Owner, name: String): String = JsonPath.read(
        mockMvc.post("/api/services") {
            header("Authorization", "Bearer ${owner.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"$name"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        "$.uuid",
    )

    /** 발급 응답의 (키 uuid, 원문). */
    private fun issue(owner: Owner, service: String): Pair<String, String> {
        val body = mockMvc.post("/api/services/$service/keys") {
            header("Authorization", "Bearer ${owner.token}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"블로그 서버"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return JsonPath.read<String>(body, "$.key.uuid") to JsonPath.read(body, "$.secret")
    }

    private fun template(owner: Owner, service: String, name: String): String = JsonPath.read(
        mockMvc.post("/api/templates") {
            header("Authorization", "Bearer ${owner.token}")
            header("X-Workspace-Id", service)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"$name","option":{"type":"text-generate","prompt":"{{request}}"},"model":"${SharedTestConfig.FAKE_TEXT_MODEL}"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        "$.uuid",
    )

    @Test
    fun `키로 부르면 헤더 없이도 키의 서비스가 범위이고 그 서비스의 템플릿을 실행한다`() {
        val owner = owner()
        val blog = newService(owner, "블로그")
        template(owner, defaultService(owner), "기본 서비스 템플릿")
        val blogTemplate = template(owner, blog, "블로그 요약")
        val (_, secret) = issue(owner, blog)

        mockMvc.get("/api/templates") { header("Authorization", "Bearer $secret") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].name") { value("블로그 요약") }
        }
        val submitted = mockMvc.post("/api/templates/$blogTemplate/jobs") {
            header("Authorization", "Bearer $secret")
            contentType = MediaType.APPLICATION_JSON
            content = """{"variables":{"request":"요약해 줘"}}"""
        }.andExpect { status { isAccepted() } }.andReturn().response.contentAsString
        val job: String = JsonPath.read(submitted, "$.uuid")
        mockMvc.get("/api/jobs/$job") { header("Authorization", "Bearer $secret") }.andExpect { status { isOk() } }
        // 같은 작업을 주인이 로그인으로 볼 때도 키의 서비스에 속해 있다.
        mockMvc.get("/api/jobs/$job") {
            header("Authorization", "Bearer ${owner.token}")
            header("X-Workspace-Id", blog)
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `키로 다른 서비스를 지정하면 같은 사용자의 서비스라도 거부한다`() {
        val owner = owner()
        val blog = newService(owner, "블로그")
        val (_, secret) = issue(owner, blog)

        mockMvc.get("/api/templates") {
            header("Authorization", "Bearer $secret")
            header("X-Workspace-Id", defaultService(owner))
        }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/templates") {
            header("Authorization", "Bearer $secret")
            header("X-Workspace-Id", blog)
        }.andExpect { status { isOk() } }
        mockMvc.get("/api/templates") {
            header("Authorization", "Bearer $secret")
            header("X-Workspace-Id", "not-a-uuid")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `키로는 서비스 · 키 관리와 내 정보 같은 계정 수준 경로를 열지 못한다`() {
        val owner = owner()
        val blog = newService(owner, "블로그")
        val (_, secret) = issue(owner, blog)

        mockMvc.get("/api/services") { header("Authorization", "Bearer $secret") }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/services/$blog/keys") { header("Authorization", "Bearer $secret") }.andExpect { status { isForbidden() } }
        mockMvc.post("/api/services/$blog/keys") {
            header("Authorization", "Bearer $secret")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"키로 만든 키"}"""
        }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/me") { header("Authorization", "Bearer $secret") }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/models?type=text-generate") { header("Authorization", "Bearer $secret") }.andExpect { status { isOk() } }
    }

    @Test
    fun `폐기 · 만료 · 모르는 키와 비활성 사용자의 키는 인증하지 않는다`() {
        val owner = owner()
        val blog = newService(owner, "블로그")
        val (revokedId, revoked) = issue(owner, blog)
        val (expiredId, expired) = issue(owner, blog)
        val (_, active) = issue(owner, blog)
        mockMvc.delete("/api/services/$blog/keys/$revokedId") { header("Authorization", "Bearer ${owner.token}") }
            .andExpect { status { isNoContent() } }
        jdbc.update("UPDATE service_api_keys SET expires_at = now() - interval '1 second' WHERE uuid = ?", UUID.fromString(expiredId))
        // 테스트 전체가 한 트랜잭션이라, SQL 로 바꾼 값을 앞서 읽어 둔 엔티티가 가리지 않게 비운다.
        em.flush(); em.clear()

        listOf(revoked, expired, "pawb_unknown").forEach { secret ->
            mockMvc.get("/api/templates") { header("Authorization", "Bearer $secret") }.andExpect { status { isUnauthorized() } }
        }
        mockMvc.get("/api/templates") { header("Authorization", "Bearer $active") }.andExpect { status { isOk() } }

        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE uuid = ?", owner.uuid)
        em.flush(); em.clear()
        mockMvc.get("/api/templates") { header("Authorization", "Bearer $active") }.andExpect { status { isUnauthorized() } }
    }
}
