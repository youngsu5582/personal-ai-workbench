package dev.joyson.aiworkbench.workspace

import com.jayway.jsonpath.JsonPath
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val userRegistry: UserRegistry,
    private val tokenService: TokenService,
) : IntegrationTest() {
    private fun token(): String {
        val user = userRegistry.resolveOrRegister(
            RegisterIdentityCommand.of("https://accounts.google.com", UUID.randomUUID().toString(), "사용자", null),
        )
        return tokenService.issueAccessToken(user.uuid, user.displayName).accessToken
    }

    @Test
    fun `새 사용자에게 기본 서비스가 있고 서비스는 소유자만 볼 수 있다`() {
        val owner = token()
        val stranger = token()
        mockMvc.get("/api/services") { header("Authorization", "Bearer $owner") }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].isDefault") { value(true) }
        }
        val response = mockMvc.post("/api/services") {
            header("Authorization", "Bearer $owner")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"블로그"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val uuid: String = JsonPath.read(response, "$.uuid")

        mockMvc.get("/api/services/$uuid") { header("Authorization", "Bearer $stranger") }
            .andExpect { status { isNotFound() } }
        mockMvc.put("/api/services/$uuid") {
            header("Authorization", "Bearer $stranger")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"변경"}"""
        }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/services/$uuid") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isOk() }; jsonPath("$.name") { value("블로그") } }
        mockMvc.get("/api/services") { header("Authorization", "Bearer $owner") }
            .andExpect { jsonPath("$.length()") { value(2) } }
    }

    @Test
    fun `다시 로그인해도 기본 서비스가 중복되지 않는다`() {
        val command = RegisterIdentityCommand.of("https://accounts.google.com", UUID.randomUUID().toString(), "사용자", null)
        val first = userRegistry.resolveOrRegister(command)
        val second = userRegistry.resolveOrRegister(command)
        assertEquals(first.uuid, second.uuid)
        val token = tokenService.issueAccessToken(first.uuid, first.displayName).accessToken
        mockMvc.get("/api/services") { header("Authorization", "Bearer $token") }
            .andExpect { jsonPath("$.length()") { value(1) } }
    }
}
