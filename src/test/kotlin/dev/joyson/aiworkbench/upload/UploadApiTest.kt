package dev.joyson.aiworkbench.upload

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.storage.PresignedUploadIssuer
import dev.joyson.aiworkbench.storage.s3.GarageTestStorage
import dev.joyson.aiworkbench.storage.s3.S3Clients
import dev.joyson.aiworkbench.storage.s3.S3FileStorage
import dev.joyson.aiworkbench.storage.s3.S3PresignedUploadIssuer
import dev.joyson.aiworkbench.upload.domain.UploadSpec
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** API가 발급한 주소를 실제 Garage에 사용해 인증·서명·전송까지 검증한다. */
@Import(UploadApiTest.SigningConfig::class)
class UploadApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val tokenService: TokenService,
    private val userRegistry: UserRegistry,
    private val jsonMapper: JsonMapper,
) : IntegrationTest() {
    private val user by lazy {
        userRegistry.resolveOrRegister(
            RegisterIdentityCommand.of(
                issuer = "https://accounts.google.com",
                subject = "upload-api-test",
                displayName = "upload-owner",
                email = "upload@example.com",
            ),
        )
    }
    private val token by lazy { tokenService.issueAccessToken(user.uuid, user.displayName).accessToken }

    private fun request(payload: String, withToken: Boolean = true) = mockMvc.post("/api/uploads") {
        if (withToken) header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = payload
    }

    @Test
    fun `발급한 주소는 인증된 사용자 경로에 실제로 업로드할 수 있다`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val response = request("""{"contentType":"image/png","contentLength":4,"ownerUuid":"다른-사용자","key":"기존-파일"}""")
            .andExpect {
                status { isOk() }
                header { string("Cache-Control", "no-store") }
                jsonPath("$.uuid") { exists() }
                jsonPath("$.method") { value("PUT") }
                jsonPath("$.headers['Content-Type']") { value("image/png") }
                jsonPath("$.headers['Content-Length']") { value("4") }
            }.andReturn().response
        val body = jsonMapper.readTree(response.contentAsString)
        val url = URI.create(body.get("url").asText())
        val key = "users/${user.uuid}/uploads/${body.get("uuid").asText()}"
        assertTrue(url.path.endsWith("/$key"))

        val uploaded = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(url)
                .header("Content-Type", body.get("headers").get("Content-Type").asText())
                .PUT(HttpRequest.BodyPublishers.ofByteArray(png))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, uploaded.statusCode())
        val storage = S3FileStorage(S3Clients.of(GarageTestStorage.properties()), GarageTestStorage.BUCKET)
        assertContentEquals(png, storage.read(key))
    }

    @Test
    fun `발급할 때마다 별도 위치를 준다`() {
        val payload = """{"contentType":"image/webp","contentLength":1}"""
        val first = jsonMapper.readTree(request(payload).andExpect { status { isOk() } }.andReturn().response.contentAsString)
        val second = jsonMapper.readTree(request(payload).andExpect { status { isOk() } }.andReturn().response.contentAsString)
        assertNotEquals(first.get("uuid").asText(), second.get("uuid").asText())
    }

    @Test
    fun `JPEG 와 최대 크기는 발급할 수 있다`() {
        request("""{"contentType":"image/jpeg","contentLength":${UploadSpec.MAX_CONTENT_LENGTH}}""")
            .andExpect { status { isOk() } }
    }

    @Test
    fun `허용하지 않는 형식은 400 이다`() {
        listOf("text/html", "image/svg+xml", "image/gif", "", "image/png; charset=utf-8").forEach {
            request("""{"contentType":"$it","contentLength":1}""")
                .andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `0 이하와 상한 초과 크기는 400 이다`() {
        listOf(0L, -1L, UploadSpec.MAX_CONTENT_LENGTH + 1).forEach {
            request("""{"contentType":"image/png","contentLength":$it}""")
                .andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `필수 값이 없거나 null 이면 400 이다`() {
        listOf(
            "{}",
            """{"contentType":"image/png"}""",
            """{"contentLength":1}""",
            """{"contentType":null,"contentLength":1}""",
            """{"contentType":"image/png","contentLength":null}""",
        ).forEach { request(it).andExpect { status { isBadRequest() } } }
    }

    @Test
    fun `토큰이 없으면 401 이다`() {
        request("""{"contentType":"image/png","contentLength":1}""", withToken = false)
            .andExpect { status { isUnauthorized() } }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SigningConfig {
        // 공통 테스트 설정은 local 보관소이므로, 이 테스트에서만 실제 서명 발급자를 붙인다.
        @Bean
        @Primary
        fun uploadTestIssuer(): PresignedUploadIssuer = S3PresignedUploadIssuer(
            S3Clients.presigner(GarageTestStorage.properties()),
            GarageTestStorage.BUCKET,
            Duration.ofMinutes(5),
        )
    }
}
