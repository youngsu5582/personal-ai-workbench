package dev.joyson.aiworkbench.auth

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.auth.api.OAuth2LoginSuccessHandler
import dev.joyson.aiworkbench.googleLogin
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Google 로그인이 끝난 뒤 어디로 보내는지 본다.
 *
 * 인가 엔드포인트로 가다가 온 로그인과 콘솔에서 바로 온 로그인은 세션을 다르게 다룬다.
 */
class OAuth2LoginSuccessHandlerTest @Autowired constructor(
    private val handler: OAuth2LoginSuccessHandler,
) : IntegrationTest() {

    private val login = googleLogin(UUID.randomUUID())

    /** 로그인하러 가기 전에 [path] 로 왔던 요청을 세션에 남긴다. */
    private fun sessionAfterVisiting(path: String, query: String? = null): MockHttpSession {
        val visited = MockHttpServletRequest("GET", path).apply { queryString = query }
        HttpSessionRequestCache().saveRequest(visited, MockHttpServletResponse())
        return visited.session as MockHttpSession
    }

    private fun loginCallback(session: MockHttpSession): Pair<MockHttpServletResponse, MockHttpSession> {
        val request = MockHttpServletRequest("GET", "/login/oauth2/code/google").apply { setSession(session) }
        val response = MockHttpServletResponse()
        handler.onAuthenticationSuccess(request, response, login)
        return response to session
    }

    @Test
    fun `인가 엔드포인트로 가다가 로그인했으면 그 요청으로 돌려보내고 세션을 남긴다`() {
        val (response, session) = loginCallback(sessionAfterVisiting("/oauth2/authorize", "response_type=code&client_id=workbench-console"))

        val redirected = assertNotNull(response.redirectedUrl)
        assertTrue(redirected.contains("/oauth2/authorize?response_type=code&client_id=workbench-console"), "redirectedUrl=$redirected")
        assertFalse(session.isInvalid, "세션이 지워지면 인가 서버가 로그인한 사람을 알 수 없다")
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE), "인가 흐름에서는 handoff 토큰을 발급하지 않는다")
    }

    @Test
    fun `콘솔에서 바로 로그인했으면 handoff 쿠키를 주고 세션을 지운다`() {
        val (response, session) = loginCallback(MockHttpSession())

        assertEquals("/?login=success", response.redirectedUrl)
        assertNotNull(response.getHeader(HttpHeaders.SET_COOKIE))
        assertTrue(session.isInvalid)
    }

    @Test
    fun `인가 엔드포인트가 아닌 곳으로 가다가 로그인했으면 handoff 로 간다`() {
        val (response, session) = loginCallback(sessionAfterVisiting("/somewhere"))

        assertEquals("/?login=success", response.redirectedUrl)
        assertTrue(session.isInvalid)
    }
}
