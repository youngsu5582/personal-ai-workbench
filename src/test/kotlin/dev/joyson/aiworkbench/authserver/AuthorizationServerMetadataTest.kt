package dev.joyson.aiworkbench.authserver

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 인가 서버가 밖으로 여는 것 — 메타데이터와 JWKS — 을 실제 서블릿 컨테이너로 확인한다.
 *
 * 필터 체인이 셋이라, 어느 체인이 어느 경로를 받는지는 실제 요청으로만 드러난다.
 * 리다이렉트는 따라가지 않는다. 따라가면 Google 로그인 시작 경로가 어디로 보내는지 볼 수 없다.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthorizationServerMetadataTest @Autowired constructor(
    @param:LocalServerPort private val port: Int,
) {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    private val json = JsonMapper.builder().build()

    private fun get(path: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun getJson(path: String): JsonNode {
        val response = get(path)
        assertEquals(200, response.statusCode(), path)
        return json.readTree(response.body())
    }

    @Test
    fun `메타데이터는 설정한 발급자와 그 주소로 만든 엔드포인트를 알린다`() {
        val metadata = getJson("/.well-known/oauth-authorization-server")

        assertEquals("https://workbench.test", metadata["issuer"].asString())
        assertEquals("https://workbench.test/oauth2/jwks", metadata["jwks_uri"].asString())
        assertEquals("https://workbench.test/oauth2/token", metadata["token_endpoint"].asString())
    }

    @Test
    fun `JWKS 는 RSA 공개키만 내보낸다`() {
        val keys = getJson("/oauth2/jwks")["keys"]

        assertEquals(1, keys.size())
        val key = keys[0]
        assertEquals("RSA", key["kty"].asString())
        assertEquals("RS256", key["alg"].asString())
        listOf("n", "e", "kid").forEach { assertTrue(key.has(it), "$it 가 없다") }
        listOf("d", "p", "q", "dp", "dq", "qi").forEach { assertFalse(key.has(it), "개인키의 $it 가 JWKS 로 나갔다") }
    }

    @Test
    fun `Google 로그인 시작 경로는 인가 서버 체인이 가로채지 않는다`() {
        val response = get("/oauth2/authorization/google")

        assertEquals(302, response.statusCode())
        val location = response.headers().firstValue("Location").orElse("")
        assertTrue(location.startsWith("https://accounts.google.com/"), "Location=$location")
    }

    @Test
    fun `등록된 클라이언트가 없어 토큰 엔드포인트는 인증에 실패한다`() {
        val response = client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&client_id=nobody&client_secret=x"))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(401, response.statusCode())
    }
}
