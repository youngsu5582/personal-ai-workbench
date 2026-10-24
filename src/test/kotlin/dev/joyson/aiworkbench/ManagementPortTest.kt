package dev.joyson.aiworkbench

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * actuator 는 management 포트에만 있다.
 *
 * ingress 는 앱 포트만 넘긴다. 토큰 없이 연 health·prometheus 가 인터넷에 닿지 않는 이유는 이 포트 분리 하나라,
 * "앱 포트에는 없다" 를 따로 확인한다.
 *
 * kubelet 은 200~399 를 모두 성공으로 본다. probe 경로가 인증 대상이 되면 로그인 리다이렉트를 받고도
 * 통과하므로, 토큰 없이 열려 있는지는 여기서만 드러난다.
 *
 * management 포트는 MockMvc 로 닿지 않아 실제 포트를 띄운다. 그래서 [IntegrationTest] 와 컨텍스트를 나누지 못한다.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SharedTestConfig::class)
class ManagementPortTest {

    @LocalServerPort
    private var appPort = 0

    @LocalManagementPort
    private var managementPort = 0

    // 따라가면 로그인 리다이렉트가 Google 까지 간다. 받은 그대로 본다.
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    @Test
    fun `배포 설정도 actuator 를 앱 포트에서 뗀다`() {
        // 테스트 컨텍스트는 application.yaml 을 읽지 않는다. 배포에서 이 값이 사라지면 아래 검증이 무의미해진다.
        val deployed = YamlPropertySourceLoader().load("application", ClassPathResource("application.yaml")).single()

        assertEquals("8081", deployed.getProperty("management.server.port")?.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["/actuator/health", "/actuator/health/readiness", "/actuator/prometheus"])
    fun `앱 포트에는 actuator 가 없다`(path: String) {
        assertEquals(404, get(appPort, path).statusCode())
    }

    @ParameterizedTest
    @ValueSource(strings = ["/actuator/health/readiness", "/actuator/health/liveness"])
    fun `kubelet 은 토큰 없이 probe 경로를 본다`(path: String) {
        val response = get(managementPort, path)

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body())
    }

    @Test
    fun `health 는 하위 구성요소를 드러내지 않는다`() {
        val response = get(managementPort, "/actuator/health")

        assertEquals(200, response.statusCode())
        assertFalse(response.body().contains("components"), response.body())
    }

    @Test
    fun `Prometheus 는 토큰 없이 메트릭을 긁어간다`() {
        val response = get(managementPort, "/actuator/prometheus")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("jvm_memory_used_bytes"), response.body().take(500))
    }

    /**
     * 대시보드와 알림은 Micrometer 이름이 아니라 레지스트리가 바꾼 이름에 묶인다(단위 접미사).
     * Provider 호출 지표는 첫 호출 때 생기므로 여기서 보지 않는다.
     */
    @Test
    fun `Task 시도 지표가 Prometheus 이름으로 나간다`() {
        val body = get(managementPort, "/actuator/prometheus").body()

        listOf(
            """workbench_generation_task_attempt_seconds_count{outcome="retried"}""",
        ).forEach { assertTrue(body.contains(it), "$it 이 없다") }
    }

    private fun get(port: Int, path: String): HttpResponse<String> =
        client.send(HttpRequest.newBuilder(URI("http://localhost:$port$path")).build(), HttpResponse.BodyHandlers.ofString())
}
