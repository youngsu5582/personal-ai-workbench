package dev.joyson.aiworkbench.authserver

import dev.joyson.aiworkbench.authserver.config.AuthorizationServerConfig
import dev.joyson.aiworkbench.googleLogin
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 콘솔 백엔드 역할 — 인가 코드를 받아 토큰으로 바꾼다. 로그인을 마친 사용자로 시작한다. */
class ConsoleClient(private val mockMvc: MockMvc, private val properties: AuthserverProperties, private val userUuid: UUID) {

    private val json = JsonMapper.builder().build()
    private val verifier = "verifier-" + UUID.randomUUID() + UUID.randomUUID()

    fun basic(): String =
        "Basic " + Base64.getEncoder().encodeToString("${AuthorizationServerConfig.CONSOLE_CLIENT_ID}:${properties.console.clientSecret}".toByteArray())

    /** 인가 요청을 보내 인가 코드를 받는다. */
    fun authorizationCode(): String {
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val uri = UriComponentsBuilder.fromPath("/oauth2/authorize")
            .queryParam("response_type", "code")
            .queryParam("client_id", AuthorizationServerConfig.CONSOLE_CLIENT_ID)
            .queryParam("redirect_uri", properties.console.redirectUri)
            .queryParam("state", "state-1")
            .queryParam("code_challenge", challenge)
            .queryParam("code_challenge_method", "S256")
            .encode().build().toUri()
        val location = assertNotNull(
            mockMvc.get(uri) {
                accept = MediaType.TEXT_HTML
                with(authentication(googleLogin(userUuid)))
            }.andReturn().response.getHeader("Location"),
        )
        return assertNotNull(UriComponentsBuilder.fromUriString(location).build().queryParams.getFirst("code"), "Location=$location")
    }

    /** 인가 코드를 토큰으로 바꾼다. 응답의 JSON 을 돌려준다. */
    fun exchange(code: String): JsonNode {
        val response = mockMvc.post("/oauth2/token") {
            header("Authorization", basic())
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("grant_type", "authorization_code")
            param("code", code)
            param("redirect_uri", properties.console.redirectUri)
            param("code_verifier", verifier)
        }.andReturn().response
        assertEquals(200, response.status, response.contentAsString)
        return json.readTree(response.contentAsString)
    }

    /** 인가 코드를 토큰으로 바꾸는 요청을 보내고 응답 상태만 돌려준다. 성공해야 하는 곳은 [exchange] 를 쓴다. */
    fun exchangeStatus(code: String): Int = mockMvc.post("/oauth2/token") {
        header("Authorization", basic())
        contentType = MediaType.APPLICATION_FORM_URLENCODED
        param("grant_type", "authorization_code")
        param("code", code)
        param("redirect_uri", properties.console.redirectUri)
        param("code_verifier", verifier)
    }.andReturn().response.status

    /** 인가 요청부터 코드 교환까지. */
    fun login(): JsonNode = exchange(authorizationCode())
}
