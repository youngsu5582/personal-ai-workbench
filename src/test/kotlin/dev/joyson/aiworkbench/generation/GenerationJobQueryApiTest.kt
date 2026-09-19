package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.Test

/**
 * 폴링으로 보이는 것과 보이면 안 되는 것을 고정한다.
 *
 * 남의 Job 을 403 으로 거절하면 "그 uuid 는 존재한다" 가 새어 나간다. 그래서 404 다 —
 * 없는 것과 남의 것이 바깥에서 구분되지 않아야 한다.
 *
 * 테스트 프로필은 워커가 꺼져 있어(`workbench.generation.worker.enabled: false`)
 * 접수한 Job 이 그대로 머문다. 그래서 진행 상황이 결정적으로 관측된다.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(GenerationJobQueryApiTest.FakeProviderConfig::class)
class GenerationJobQueryApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val tokenService: TokenService,
    private val userRegistry: UserRegistry,
) {

    @TestConfiguration
    class FakeProviderConfig {
        @Bean
        fun queryTestProvider(): ExternalApiProvider = object : ExternalApiProvider {
            override val name = "fake"
            override fun supports(model: String) = model == FAKE_MODEL
            override fun generate(model: String, request: ExternalApiGenerateRequest) =
                ExternalApiGenerateResponse(result = emptyList())
        }
    }

    private fun tokenOf(subject: String): String {
        val user = userRegistry.resolveOrRegister(
            RegisterIdentityCommand.of(
                issuer = "https://accounts.google.com",
                subject = subject,
                displayName = subject,
                email = "$subject@example.com",
            ),
        )
        return tokenService.issueAccessToken(user.uuid, user.displayName).accessToken
    }

    private fun submit(token: String, taskCount: Int): String =
        mockMvc.post("/api/jobs") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"option":$OPTION,"model":"$FAKE_MODEL","taskCount":$taskCount}"""
        }.andReturn().response.contentAsString
            .substringAfter("\"uuid\":\"").substringBefore("\"")

    private fun query(uuid: String, token: String?) =
        mockMvc.get("/api/jobs/$uuid") {
            if (token != null) header("Authorization", "Bearer $token")
        }

    @Test
    fun `자기 Job 을 조회하면 수명과 진행 상황이 온다`() {
        val token = tokenOf("query-owner")
        val uuid = submit(token, taskCount = 2)

        query(uuid, token).andExpect {
            status { isOk() }
            jsonPath("$.uuid") { value(uuid) }
            jsonPath("$.status") { value("DISPATCHED") }
            jsonPath("$.model") { value(FAKE_MODEL) }
            // 워커가 꺼져 있으므로 아무것도 끝나지 않았다.
            jsonPath("$.progress.total") { value(2) }
            jsonPath("$.progress.succeeded") { value(0) }
            jsonPath("$.progress.failed") { value(0) }
            jsonPath("$.progress.remaining") { value(2) }
        }
    }

    @Test
    fun `남의 Job 은 존재 여부조차 알려주지 않는다`() {
        val uuid = submit(tokenOf("query-owner-2"), taskCount = 1)

        query(uuid, tokenOf("query-stranger")).andExpect { status { isNotFound() } }
    }

    @Test
    fun `없는 Job 은 404 다`() {
        query(UUID.randomUUID().toString(), tokenOf("query-owner-3"))
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `토큰이 없으면 401 이다`() {
        val uuid = submit(tokenOf("query-owner-4"), taskCount = 1)

        query(uuid, token = null).andExpect { status { isUnauthorized() } }
    }

    companion object {
        private const val FAKE_MODEL = "fake-model"
        private val OPTION =
            """{"type":"text-to-image","prompt":"고양이","size":{"type":"ratio","ratio":"1:1","resolution":"1k"}}"""
    }
}
