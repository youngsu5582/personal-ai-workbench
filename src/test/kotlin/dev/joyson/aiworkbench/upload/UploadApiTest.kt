package dev.joyson.aiworkbench.upload

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.storage.BlobKey
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.PresignedUploadIssuer
import dev.joyson.aiworkbench.storage.Sha256
import dev.joyson.aiworkbench.storage.s3.GarageTestStorage
import dev.joyson.aiworkbench.storage.s3.S3Clients
import dev.joyson.aiworkbench.storage.s3.S3FileStorage
import dev.joyson.aiworkbench.storage.s3.S3PresignedUploadIssuer
import dev.joyson.aiworkbench.upload.domain.UploadSpec
import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import jakarta.persistence.EntityManager
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** API 가 발급한 주소를 실제 Garage 에 사용해 인증·서명·전송까지 검증한다. */
@Import(UploadApiTest.SigningConfig::class)
class UploadApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val tokenService: TokenService,
    private val userRegistry: UserRegistry,
    private val jsonMapper: JsonMapper,
    private val uploadedFileRepository: UploadedFileRepository,
    private val em: EntityManager,
    private val fileStorage: FileStorage,
) : IntegrationTest() {
    private val user by lazy {
        userRegistry.resolveOrRegister(RegisterIdentityCommand.of(
            issuer = "https://accounts.google.com", subject = "upload-api-test",
            displayName = "upload-owner", email = "upload@example.com",
        ))
    }
    private val token by lazy { tokenService.issueAccessToken(user.uuid, user.displayName).accessToken }
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
    private val digest = Sha256.of(png)

    private fun payload(type: String = "image/png", size: Long = png.size.toLong(), hash: String = digest.hex) =
        """{"contentType":"$type","contentLength":$size,"sha256":"$hash"}"""

    private fun request(payload: String = payload(), withToken: Boolean = true) = mockMvc.post("/api/uploads") {
        if (withToken) header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = payload
    }

    private fun issue(payload: String = payload()) = jsonMapper.readTree(
        request(payload).andExpect { status { isOk() } }.andReturn().response.contentAsString,
    )

    @Test
    fun `발급한 주소는 인증된 사용자의 해시 경로에 실제로 업로드할 수 있다`() {
        val response = request(payload().dropLast(1) + ""","ownerUuid":"다른-사용자","key":"기존-파일"}""")
            .andExpect {
                status { isOk() }
                header { string("Cache-Control", "no-store") }
                jsonPath("$.uploadRequired") { value(true) }
                jsonPath("$.method") { value("PUT") }
                jsonPath("$.headers['x-amz-checksum-sha256']") { value(digest.base64()) }
                jsonPath("$.headers['x-amz-sdk-checksum-algorithm']") { value("SHA256") }
            }.andReturn().response
        val body = jsonMapper.readTree(response.contentAsString)
        val url = URI.create(body.get("url").asText())
        val key = BlobKey.uploaded(user.uuid, digest, "image/png").value
        assertTrue(url.path.endsWith("/$key"))

        val uploaded = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(url)
                .header("Content-Type", "image/png")
                .header("x-amz-checksum-sha256", body.get("headers").get("x-amz-checksum-sha256").asText())
                .header("x-amz-sdk-checksum-algorithm", "SHA256")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(png)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, uploaded.statusCode())
        val storage = S3FileStorage(S3Clients.of(GarageTestStorage.properties()), GarageTestStorage.BUCKET)
        assertContentEquals(png, storage.read(key))
    }

    @Test
    fun `발급하면 SHA-256 과 선언 조건을 DB 에 남긴다`() {
        val body = issue()
        val uuid = UUID.fromString(body.get("uuid").asText())
        em.flush()
        em.clear()
        val file = assertNotNull(uploadedFileRepository.findByUuidAndOwnerUserUuid(uuid, user.uuid))
        assertEquals(UploadStatus.PENDING, file.status)
        assertEquals(digest.hex, file.sha256)
        assertEquals(BlobKey.uploaded(user.uuid, digest, "image/png").value, file.storageKey)
        assertEquals(png.size.toLong(), file.metadata.fileSize)
        assertNull(uploadedFileRepository.findByUuidAndOwnerUserUuid(uuid, UUID.randomUUID()))
    }

    @Test
    fun `아직 올리지 않은 같은 파일은 UUID 를 재사용하고 주소를 발급한다`() {
        val first = issue()
        val second = issue()
        assertEquals(first.get("uuid").asText(), second.get("uuid").asText())
        assertTrue(second.get("uploadRequired").asBoolean())
    }

    @Test
    fun `이미 올라온 파일은 발급 시 판정하고 PUT 없이 재사용한다`() {
        val first = issue()
        val uuid = UUID.fromString(first.get("uuid").asText())
        val file = assertNotNull(uploadedFileRepository.findByUuidAndOwnerUserUuid(uuid, user.uuid))
        fileStorage.put(file.storageKey, png, "image/png")

        request().andExpect {
            status { isOk() }
            jsonPath("$.uuid") { value(uuid.toString()) }
            jsonPath("$.uploadRequired") { value(false) }
            jsonPath("$.url") { doesNotExist() }
            jsonPath("$.method") { doesNotExist() }
            jsonPath("$.headers") { isEmpty() }
        }
        assertEquals(UploadStatus.READY, uploadedFileRepository.findByUuidAndOwnerUserUuid(uuid, user.uuid)?.status)
    }

    @Test
    fun `잘못된 선언을 정정하면 같은 UUID 로 다시 올릴 수 있다`() {
        val first = issue(payload(type = "image/jpeg"))
        val uuid = UUID.fromString(first.get("uuid").asText())
        val file = assertNotNull(uploadedFileRepository.findByUuidAndOwnerUserUuid(uuid, user.uuid))
        fileStorage.put(file.storageKey, png, "image/jpeg")

        // 같은 형식의 재요청은 불합격을 다시 열지 않는다.
        request(payload(type = "image/jpeg")).andExpect { status { isBadRequest() } }
        assertEquals(UploadStatus.REJECTED, uploadedFileRepository.findByUuidAndOwnerUserUuid(uuid, user.uuid)?.status)
        assertNull(fileStorage.read(file.storageKey))

        val corrected = issue()
        assertEquals(uuid.toString(), corrected.get("uuid").asText())
        assertTrue(corrected.get("uploadRequired").asBoolean())
        val reopened = assertNotNull(uploadedFileRepository.findByUuidAndOwnerUserUuid(uuid, user.uuid))
        assertEquals(UploadStatus.PENDING, reopened.status)
        assertEquals("image/png", reopened.metadata.mimeType)
        assertEquals(BlobKey.uploaded(user.uuid, digest, "image/png").value, reopened.storageKey)
        fileStorage.put(reopened.storageKey, png, "image/png")
        assertEquals(false, issue().get("uploadRequired").asBoolean())
    }

    @Test
    fun `PENDING 파일의 형식이나 크기를 바꾼 요청은 400 이다`() {
        issue()
        request(payload(type = "image/jpeg")).andExpect { status { isBadRequest() } }
        request(payload(size = png.size + 1L)).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `JPEG 와 최대 크기는 발급할 수 있다`() {
        request(payload(type = "image/jpeg", size = UploadSpec.MAX_CONTENT_LENGTH))
            .andExpect { status { isOk() } }
    }

    @Test
    fun `허용하지 않는 형식은 400 이다`() {
        listOf("text/html", "image/svg+xml", "image/gif", "", "image/png; charset=utf-8").forEach {
            request(payload(type = it)).andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `0 이하와 상한 초과 크기는 400 이다`() {
        listOf(0L, -1L, UploadSpec.MAX_CONTENT_LENGTH + 1).forEach {
            request(payload(size = it)).andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `SHA-256 은 소문자 hex 64자만 받는다`() {
        listOf("", "a".repeat(63), "a".repeat(65), "A".repeat(64), "z".repeat(64)).forEach {
            request(payload(hash = it)).andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `필수 값이 없거나 null 이면 400 이다`() {
        listOf(
            "{}", """{"contentType":"image/png"}""", """{"contentLength":1}""",
            """{"contentType":null,"contentLength":1,"sha256":"${digest.hex}"}""",
            """{"contentType":"image/png","contentLength":null,"sha256":"${digest.hex}"}""",
            """{"contentType":"image/png","contentLength":1}""",
            """{"contentType":"image/png","contentLength":1,"sha256":null}""",
        ).forEach { request(it).andExpect { status { isBadRequest() } } }
    }

    @Test
    fun `토큰이 없으면 401 이다`() {
        request(withToken = false).andExpect { status { isUnauthorized() } }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SigningConfig {
        @Bean
        @Primary
        fun uploadTestIssuer(): PresignedUploadIssuer = S3PresignedUploadIssuer(
            S3Clients.presigner(GarageTestStorage.properties()), GarageTestStorage.BUCKET, Duration.ofMinutes(5),
        )
    }
}
