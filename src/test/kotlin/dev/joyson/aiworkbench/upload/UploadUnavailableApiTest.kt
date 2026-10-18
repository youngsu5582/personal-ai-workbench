package dev.joyson.aiworkbench.upload

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.FileStorageException
import dev.joyson.aiworkbench.storage.PresignedUploadIssuer
import dev.joyson.aiworkbench.storage.Sha256
import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@Import(UploadUnavailableApiTest.SigningConfig::class)
class UploadUnavailableApiTest @Autowired constructor(
    private val mvc: MockMvc,
    private val tokens: TokenService,
    private val users: UserRegistry,
    private val mapper: JsonMapper,
    private val repository: UploadedFileRepository,
) : IntegrationTest() {
    @MockitoBean(name = "inMemoryFileStorage")
    private lateinit var storage: FileStorage

    @Test
    fun `중복 확인 중 보관소 장애는 503 이고 PENDING 과 내부 정보는 보존한다`() {
        val user = users.resolveOrRegister(RegisterIdentityCommand.of(
            issuer = "https://accounts.google.com", subject = "upload-unavailable",
            displayName = "owner", email = "unavailable@example.com",
        ))
        val token = tokens.issueAccessToken(user.uuid, user.displayName).accessToken
        val digest = Sha256.of(byteArrayOf(1))
        fun request() = mvc.post("/api/uploads") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"image/png","contentLength":1,"sha256":"${digest.hex}"}"""
        }
        val first = request().andExpect { status { isOk() } }.andReturn().response
        val uuid = mapper.readTree(first.contentAsString).get("sha256").asText()
        val file = assertNotNull(repository.findByOwnerUserUuidAndSha256(user.uuid, uuid))
        `when`(storage.readRange(file.storageKey, 0L until 12)).thenThrow(
            FileStorageException(retryable = true, message = "internal-storage-endpoint/${file.storageKey}"),
        )

        val response = request().andExpect {
            status { isServiceUnavailable() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("업로드한 파일을 지금 확인할 수 없다") }
        }.andReturn().response
        assertFalse(response.contentAsString.contains(file.storageKey))
        assertFalse(response.contentAsString.contains("internal-storage-endpoint"))
        assertEquals(UploadStatus.PENDING, repository.findByOwnerUserUuidAndSha256(user.uuid, uuid)?.status)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SigningConfig {
        @Bean
        @Primary
        fun uploadUnavailableTestIssuer(): PresignedUploadIssuer =
            PresignedUploadIssuer { key, _, _, _ -> URI.create("https://storage.test/$key") }
    }
}
