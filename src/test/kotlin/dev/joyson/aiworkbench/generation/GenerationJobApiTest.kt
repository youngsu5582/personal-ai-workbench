package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 요청 검증이 어디서 걸리는지를 고정한다.
 *
 * 같은 잘못된 입력이라도 요청 바인딩에서 걸리면 400, 서비스까지 들어가서 터지면 500 이다.
 * 상한 없는 taskCount 가 실제로 500 을 내고 Provider 호출을 무제한으로 만들 수 있었다.
 */
class GenerationJobApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val tokenService: TokenService,
    private val userRegistry: UserRegistry,
) : IntegrationTest() {
    private val token: String by lazy {
        val user = userRegistry.resolveOrRegister(
            RegisterIdentityCommand.of(
                issuer = "https://accounts.google.com",
                subject = "generation-api-test",
                displayName = "Joyson",
                email = "joyson@example.com",
            ),
        )
        tokenService.issueAccessToken(user.uuid, user.displayName).accessToken
    }

    private fun body(
        taskCount: Int,
        option: String = OPTION,
        model: String = FAKE_MODEL,
    ) = """{"option":$option,"model":"$model","taskCount":$taskCount}"""

    private fun request(payload: String, withToken: Boolean = true) =
        mockMvc.post("/api/jobs") {
            if (withToken) header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = payload
        }

    @Test
    fun `유효한 요청은 202 와 식별자를 돌려준다`() {
        request(body(taskCount = 2)).andExpect {
            status { isAccepted() }
            jsonPath("$.uuid") { exists() }
        }
    }

    @Test
    fun `상한을 넘는 개수는 400 이다`() {
        request(body(taskCount = GenerationJob.MAX_TASK_COUNT + 1)).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `0개 이하는 400 이다`() {
        request(body(taskCount = 0)).andExpect { status { isBadRequest() } }
        request(body(taskCount = -1)).andExpect { status { isBadRequest() } }
    }

    /**
     * 접수를 통과시키면 몇 분 뒤 Task 실패로만 드러난다.
     * 그 실패는 결정적이라 재시도해도 같아서, 큐 자리와 재시도 횟수만 태운다.
     */
    @Test
    fun `다룰 수 있는 Provider 가 없는 모델은 400 이다`() {
        request(body(taskCount = 1, model = "존재하지-않는-모델"))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `model 이 없으면 400 이다`() {
        request("""{"option":$OPTION,"taskCount":1}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `option 이 없으면 400 이다`() {
        request("""{"model":"$FAKE_MODEL","taskCount":1}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `알 수 없는 생성 종류는 400 이다`() {
        request(body(1, """{"type":"text-to-hologram","prompt":"고양이"}""")).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `빈 prompt 는 400 이다`() {
        request(body(1, """{"type":"text-to-image","prompt":"  "}""")).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `토큰이 없으면 401 이다`() {
        request(body(taskCount = 1), withToken = false).andExpect { status { isUnauthorized() } }
    }

    /** 애노테이션 인자는 컴파일 타임 상수라 도메인 상수를 참조할 수 없다. 어긋나면 여기서 잡는다. */
    @Test
    fun `요청 제약의 상한이 도메인 상한과 같다`() {
        val max = GenerationRequestConstraints.maxTaskCount()
        assertEquals(GenerationJob.MAX_TASK_COUNT, max)
    }
}

private object GenerationRequestConstraints {
    fun maxTaskCount(): Int {
        val field = dev.joyson.aiworkbench.generation.api.GenerationRequest::class.java
            .getDeclaredField("taskCount")
        return field.getAnnotation(jakarta.validation.constraints.Max::class.java).value.toInt()
    }
}

private const val FAKE_MODEL = SharedTestConfig.FAKE_MODEL
private const val OPTION =
    """{"type":"text-to-image","prompt":"고양이","size":{"type":"ratio","ratio":"1:1","resolution":"1k"}}"""
