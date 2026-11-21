package dev.joyson.aiworkbench.authserver

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jwt.SignedJWT
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.authserver.config.AuthorizationServerConfig
import dev.joyson.aiworkbench.googleLogin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 인가 코드 + PKCE 흐름을 처음부터 끝까지 본다 — 인가 요청, 인가 코드, 토큰 교환, 받은 토큰의 모양.
 *
 * Google 은 거치지 않는다. 로그인을 마친 상태에서 시작한다.
 */
class AuthorizationCodeFlowTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val properties: AuthserverProperties,
) : IntegrationTest() {

    private val json = JsonMapper.builder().build()
    private val userUuid = UUID.randomUUID()
    private val redirectUri = properties.console.redirectUri

    private val verifier = "verifier-" + UUID.randomUUID() + UUID.randomUUID()
    private val challenge = challengeOf(verifier)

    private fun challengeOf(verifier: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    /** 인가 서버는 GET 요청의 파라미터를 쿼리 문자열에서만 읽는다. 그래서 주소에 직접 싣는다. */
    private fun authorize(codeChallenge: String? = challenge, loggedIn: Boolean = true, redirectUri: String = this.redirectUri): ResultActionsDsl {
        val uri = UriComponentsBuilder.fromPath("/oauth2/authorize")
            .queryParam("response_type", "code")
            .queryParam("client_id", AuthorizationServerConfig.CONSOLE_CLIENT_ID)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("state", "state-1")
            .apply {
                if (codeChallenge != null) {
                    queryParam("code_challenge", codeChallenge)
                    queryParam("code_challenge_method", "S256")
                }
            }
            .encode().build().toUri()
        return mockMvc.get(uri) {
            accept = MediaType.TEXT_HTML
            if (loggedIn) with(authentication(googleLogin(userUuid)))
        }
    }

    private fun locationOf(result: ResultActionsDsl): String = assertNotNull(result.andReturn().response.getHeader("Location"))

    private fun authorizationCode(): String {
        val location = locationOf(authorize().andExpect { status { is3xxRedirection() } })
        assertTrue(location.startsWith(redirectUri), "Location=$location")
        val params = UriComponentsBuilder.fromUriString(location).build().queryParams
        assertEquals("state-1", params.getFirst("state"))
        return assertNotNull(params.getFirst("code"), "Location=$location")
    }

    private fun exchange(code: String, codeVerifier: String = verifier): ResultActionsDsl {
        val credentials = "${AuthorizationServerConfig.CONSOLE_CLIENT_ID}:${properties.console.clientSecret}"
        return mockMvc.post("/oauth2/token") {
            header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray()))
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("grant_type", "authorization_code")
            param("code", code)
            param("redirect_uri", redirectUri)
            param("code_verifier", codeVerifier)
        }
    }

    private fun accessToken(): String {
        val body = exchange(authorizationCode()).andExpect { status { isOk() } }.andReturn().response.contentAsString
        return json.readTree(body)["access_token"].asString()
    }

    @Test
    fun `인가 코드를 토큰으로 바꾸면 RS256 으로 서명한 접근 토큰이 나온다`() {
        val jwt = SignedJWT.parse(accessToken())

        assertEquals(JWSAlgorithm.RS256, jwt.header.algorithm)
        val jwks = JWKSet.parse(mockMvc.get("/oauth2/jwks").andReturn().response.contentAsString)
        val key = assertNotNull(jwks.getKeyByKeyId(jwt.header.keyID), "토큰의 kid 가 JWKS 에 없다") as RSAKey
        assertTrue(jwt.verify(RSASSAVerifier(key)), "JWKS 의 공개키로 서명이 검증되지 않는다")
    }

    @Test
    fun `접근 토큰의 sub 는 Google 의 subject 가 아니라 워크벤치 사용자 UUID 다`() {
        val claims = SignedJWT.parse(accessToken()).jwtClaimsSet

        assertEquals(userUuid.toString(), claims.subject)
        assertEquals("https://workbench.test", claims.issuer)
        assertEquals(listOf(properties.audience), claims.audience)
        assertEquals(properties.accessTokenTtl.seconds, (claims.expirationTime.time - claims.issueTime.time) / 1000)
        assertNotNull(claims.jwtid)
    }

    @Test
    fun `토큰 응답에 리프레시 토큰은 없다`() {
        val body = exchange(authorizationCode()).andReturn().response.contentAsString

        assertFalse(json.readTree(body).has("refresh_token"))
    }

    @Test
    fun `code_verifier 가 다르면 토큰을 주지 않는다`() {
        exchange(authorizationCode(), codeVerifier = "wrong-" + verifier).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("invalid_grant") }
        }
    }

    @Test
    fun `인가 코드는 한 번만 쓸 수 있다`() {
        val code = authorizationCode()
        exchange(code).andExpect { status { isOk() } }

        exchange(code).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("invalid_grant") }
        }
    }

    @Test
    fun `code_challenge 없이 온 인가 요청은 인가 코드를 받지 못한다`() {
        val location = locationOf(authorize(codeChallenge = null).andExpect { status { is3xxRedirection() } })

        assertTrue(location.contains("error=invalid_request"), "Location=$location")
        assertFalse(location.contains("code="), "Location=$location")
    }

    @Test
    fun `등록한 것과 다른 redirect_uri 로는 돌려보내지 않는다`() {
        authorize(redirectUri = "$redirectUri/other").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `로그인하지 않은 채 인가 엔드포인트에 오면 Google 로그인으로 보낸다`() {
        val location = locationOf(authorize(loggedIn = false).andExpect { status { is3xxRedirection() } })

        assertTrue(location.endsWith("/oauth2/authorization/google"), "Location=$location")
    }

    @Test
    fun `자격 증명 없이 토큰 엔드포인트를 부르면 로그인 화면으로 보내지 않고 401 로 답한다`() {
        mockMvc.post("/oauth2/token") {
            accept = MediaType.TEXT_HTML
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("grant_type", "authorization_code")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `토큰 조회는 등록된 클라이언트에게도 막혀 있다`() {
        val credentials = "${AuthorizationServerConfig.CONSOLE_CLIENT_ID}:${properties.console.clientSecret}"
        mockMvc.post("/oauth2/introspect") {
            header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray()))
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("token", accessToken())
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `인가 서버가 발급한 토큰은 아직 API 에서 통하지 않는다`() {
        mockMvc.get("/api/me") { header("Authorization", "Bearer ${accessToken()}") }
            .andExpect { status { isUnauthorized() } }
    }
}
