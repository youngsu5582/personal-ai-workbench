package dev.joyson.aiworkbench.auth

import com.jayway.jsonpath.JsonPath
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ServiceApiKeyApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val users: UserRegistry,
    private val tokens: TokenService,
    private val jdbc: JdbcTemplate,
) : IntegrationTest() {
    private fun token(): String {
        val user = users.resolveOrRegister(RegisterIdentityCommand.of("https://accounts.google.com", UUID.randomUUID().toString(), "사용자", null))
        return tokens.issueAccessToken(user.uuid, user.displayName).accessToken
    }

    private fun workspace(token: String): String {
        val body = mockMvc.get("/api/services") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        return JsonPath.read(body, "$[0].uuid")
    }

    @Test
    fun `키 원문은 발급 시에만 반환하고 DB에는 해시만 저장한다`() {
        val owner = token()
        val workspace = workspace(owner)
        val expires = Instant.now().plusSeconds(86400).toString()
        val response = mockMvc.post("/api/services/$workspace/keys") {
            header("Authorization", "Bearer $owner")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"운영 서버","expiresAt":"$expires"}"""
        }.andExpect { status { isCreated() }; header { string("Cache-Control", "no-store") } }.andReturn().response.contentAsString
        val secret: String = JsonPath.read(response, "$.secret")
        val keyId: String = JsonPath.read(response, "$.key.uuid")
        assertTrue(secret.startsWith("pawb_"))
        val expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()))
        val savedHash = jdbc.queryForObject("SELECT key_hash FROM service_api_keys WHERE uuid = ?", String::class.java, UUID.fromString(keyId))
        assertEquals(expectedHash, savedHash)
        assertNotEquals(secret, savedHash)

        val list = mockMvc.get("/api/services/$workspace/keys") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isOk() }; jsonPath("$[0].name") { value("운영 서버") } }.andReturn().response.contentAsString
        assertFalse(list.contains(secret))
        assertFalse(list.contains(expectedHash))
        assertTrue(list.contains(expires))
    }

    @Test
    fun `키는 서비스 소유자만 관리하며 폐기 후에도 목록에서 확인한다`() {
        val owner = token()
        val stranger = token()
        val workspace = workspace(owner)
        val path = "/api/services/$workspace/keys"
        val issued = mockMvc.post(path) {
            header("Authorization", "Bearer $owner")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"첫 키"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val id: String = JsonPath.read(issued, "$.key.uuid")
        val secret: String = JsonPath.read(issued, "$.secret")
        val otherWorkspace = mockMvc.post("/api/services") {
            header("Authorization", "Bearer $owner")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"다른 서비스"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val otherId: String = JsonPath.read(otherWorkspace, "$.uuid")

        mockMvc.get(path) { header("Authorization", "Bearer $stranger") }.andExpect { status { isNotFound() } }
        mockMvc.post(path) {
            header("Authorization", "Bearer $stranger")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"침입"}"""
        }.andExpect { status { isNotFound() } }
        mockMvc.delete("$path/$id") { header("Authorization", "Bearer $stranger") }.andExpect { status { isNotFound() } }
        mockMvc.delete("/api/services/$otherId/keys/$id") { header("Authorization", "Bearer $owner") }.andExpect { status { isNotFound() } }
        mockMvc.get(path) { header("Authorization", "Bearer $secret") }.andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/services") { header("Authorization", "Bearer $secret") }.andExpect { status { isUnauthorized() } }
        mockMvc.delete("$path/$id") { header("Authorization", "Bearer $owner") }.andExpect { status { isNoContent() } }
        mockMvc.delete("$path/$id") { header("Authorization", "Bearer $owner") }.andExpect { status { isNoContent() } }
        mockMvc.delete("$path/${UUID.randomUUID()}") { header("Authorization", "Bearer $owner") }.andExpect { status { isNotFound() } }
        mockMvc.get(path) { header("Authorization", "Bearer $owner") }.andExpect {
            status { isOk() }
            jsonPath("$[0].revokedAt") { isNotEmpty() }
        }
    }

    @Test
    fun `같은 서비스에 여러 키를 발급하고 과거 만료일과 빈 이름은 거절한다`() {
        val owner = token()
        val path = "/api/services/${workspace(owner)}/keys"
        repeat(2) {
            mockMvc.post(path) {
                header("Authorization", "Bearer $owner")
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"키"}"""
            }.andExpect { status { isCreated() } }
        }
        mockMvc.get(path) { header("Authorization", "Bearer $owner") }.andExpect { jsonPath("$.length()") { value(2) } }
        listOf("""{"name":" "}""", """{"name":"키","expiresAt":"2020-01-01T00:00:00Z"}""").forEach { body ->
            mockMvc.post(path) {
                header("Authorization", "Bearer $owner")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isBadRequest() } }
        }
    }
}
