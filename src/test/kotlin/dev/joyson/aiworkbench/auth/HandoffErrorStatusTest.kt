package dev.joyson.aiworkbench.auth

import dev.joyson.aiworkbench.auth.api.HandoffCookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 실제 서블릿 컨테이너로 띄워서 에러 응답의 상태 코드를 검증한다.
 *
 * MockMvc 로는 이걸 잡지 못한다. 컨트롤러가 던진 401 은 서블릿의 ERROR dispatch 로
 * `/error` 에 재진입하는데, MockMvc 는 그 재진입에 보안 필터를 다시 태우지 않는다.
 * 그래서 `/error` 가 permitAll 이 아니면 실서버에서만 302(로그인 리다이렉트)로 조용히 뒤집힌다.
 *
 * 리다이렉트를 따라가지 않도록(NEVER) 설정하는 것이 이 테스트의 핵심이다.
 * 따라가 버리면 최종 상태 코드만 보게 되어 뒤집힘을 놓친다.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HandoffErrorStatusTest @Autowired constructor(
    @param:LocalServerPort private val port: Int,
) {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private fun get(path: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `쿠키 없이 handoff 를 부르면 리다이렉트가 아니라 401 이다`() {
        val response = get(HandoffCookie.PATH)

        assertEquals(401, response.statusCode(), "Location=${response.headers().firstValue("Location").orElse("없음")}")
    }

    @Test
    fun `토큰 없이 API 를 부르면 401 이다`() {
        val response = get("/api/me")

        assertEquals(401, response.statusCode())
    }
}
