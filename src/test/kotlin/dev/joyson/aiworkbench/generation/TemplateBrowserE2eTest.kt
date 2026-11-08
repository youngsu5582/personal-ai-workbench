package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.SharedTestConfig
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.provider.ExternalApiTextProvider
import dev.joyson.aiworkbench.provider.ExternalApiTextRequest
import dev.joyson.aiworkbench.provider.ExternalApiTextResponse
import dev.joyson.aiworkbench.provider.ReasoningEffort
import dev.joyson.aiworkbench.provider.StopReason
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import dev.joyson.aiworkbench.workspace.application.WorkspaceService
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 실제 HTTP 서버와 테스트 DB를 쓰고 모델 호출만 테스트 대역으로 바꾼다. */
@EnabledIfEnvironmentVariable(named = "WB_TEMPLATE_E2E", matches = "true")
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "workbench.generation.worker.enabled=true",
        "workbench.generation.worker.poll-interval-millis=200",
        "workbench.generation.worker.initial-delay-millis=100",
    ],
)
@Import(SharedTestConfig::class, TemplateBrowserE2eProvider::class)
class TemplateBrowserE2eTest @Autowired constructor(
    private val users: UserRegistry,
    private val tokens: TokenService,
    private val workspaces: WorkspaceService,
) {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `브라우저에서 템플릿을 만들고 서비스별로 실행한다`() {
        val subject = UUID.randomUUID().toString()
        val user = users.resolveOrRegister(
            RegisterIdentityCommand.of("https://accounts.google.com", subject, "브라우저 테스트", null),
        )
        val token = tokens.issueAccessToken(user.uuid, user.displayName).accessToken
        val otherWorkspace = workspaces.create(user.uuid, "다른 서비스").uuid
        val process = ProcessBuilder("npm", "run", "test:live")
            .directory(File("frontend"))
            .inheritIO()
            .apply {
                environment()["WB_E2E_BASE_URL"] = "http://127.0.0.1:$port"
                environment()["WB_E2E_TOKEN"] = token
                environment()["WB_E2E_OTHER_WORKSPACE"] = otherWorkspace.toString()
            }
            .start()
        try {
            assertTrue(process.waitFor(90, TimeUnit.SECONDS), "브라우저 테스트 시간 초과")
            assertEquals(0, process.exitValue(), "브라우저 테스트 실패")
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class TemplateBrowserE2eProvider {
    @Bean
    fun browserE2eTextProvider(): ExternalApiTextProvider = object : ExternalApiTextProvider {
        override val name = "browser-e2e"
        override val models = setOf("browser-e2e-text")
        override val efforts = ReasoningEffort.entries.toSet()
        override fun generateText(model: String, request: ExternalApiTextRequest): ExternalApiTextResponse {
            check(request.instructions == "당신은 편집자입니다. 친근한 말투로 답하세요.")
            check(request.prompt == "핵심을 알려줘")
            return ExternalApiTextResponse("테스트 생성 결과", StopReason.COMPLETED)
        }
    }
}
