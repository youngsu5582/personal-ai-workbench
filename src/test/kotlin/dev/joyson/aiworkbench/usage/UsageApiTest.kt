package dev.joyson.aiworkbench.usage

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.auth.application.TokenService
import dev.joyson.aiworkbench.user.RegisterIdentityCommand
import dev.joyson.aiworkbench.user.UserRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test

/**
 * 지출 조회가 무엇을 말하고 무엇을 말하지 않는지 고정한다.
 *
 * 가장 중요한 것은 **모르는 것을 0 으로 말하지 않는 것**이다. 단가표가 붙기 전에는 비용이 비어 있는데,
 * 합계만 내보내면 화면이 그것을 "이번 달 0 달러 썼다" 로 읽는다. 그건 거짓이고,
 * 거짓인 줄 모르는 채로 한도를 정하는 근거가 된다.
 */
class UsageApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val tokenService: TokenService,
    private val userRegistry: UserRegistry,
    private val recorder: ProviderCallRecorder,
) : IntegrationTest() {

    private class Owner(val uuid: UUID, val token: String)

    private fun owner(subject: String): Owner {
        val user = userRegistry.resolveOrRegister(
            RegisterIdentityCommand.of(
                issuer = "https://accounts.google.com",
                subject = subject,
                displayName = subject,
                email = "$subject@example.com",
            ),
        )
        return Owner(user.uuid, tokenService.issueAccessToken(user.uuid, user.displayName).accessToken)
    }

    private fun record(
        ownerUuid: UUID,
        model: String = "gpt-image-2",
        succeeded: Boolean = true,
        reportedUsd: BigDecimal? = null,
        calledAt: Instant = Instant.now(),
    ) = recorder.record(
        RecordProviderCallCommand(
            taskUuid = UUID.randomUUID(),
            jobUuid = UUID.randomUUID(),
            ownerUserUuid = ownerUuid,
            provider = "openai",
            model = model,
            succeeded = succeeded,
            latencyMs = 1_000,
            reportedAmount = reportedUsd,
            reportedUnit = reportedUsd?.let { ReportedUnit.USD },
            calledAt = calledAt,
        ),
    )

    @Test
    fun `비용을 모르면 0 이 아니라 모른다고 말한다`() {
        val me = owner("usage-unknown")
        record(me.uuid)
        record(me.uuid, succeeded = false)

        mockMvc.get("/api/usage") { header("Authorization", "Bearer ${me.token}") }
            .andExpect {
                status { isOk() }
                jsonPath("$.calls") { value(2) }
                jsonPath("$.succeededCalls") { value(1) }
                jsonPath("$.failedCalls") { value(1) }
                // 0 이 아니라 null 이다. 0 은 "안 썼다" 는 뜻이고 그건 거짓이다.
                jsonPath("$.costUsd") { doesNotExist() }
                jsonPath("$.costKnownCalls") { value(0) }
                jsonPath("$.costComplete") { value(false) }
            }
    }

    /** 일부만 아는 경우가 가장 위험하다 — 합계는 그럴듯한데 전체가 아니다. */
    @Test
    fun `일부만 아는 합계는 완전하지 않다고 말한다`() {
        val me = owner("usage-partial")
        record(me.uuid, reportedUsd = BigDecimal("0.19"))
        record(me.uuid)

        mockMvc.get("/api/usage") { header("Authorization", "Bearer ${me.token}") }
            .andExpect {
                status { isOk() }
                jsonPath("$.calls") { value(2) }
                jsonPath("$.costKnownCalls") { value(1) }
                jsonPath("$.costComplete") { value(false) }
                jsonPath("$.costUsd") { value("0.19000000") }
            }
    }

    @Test
    fun `모델별로 나눠 보여준다`() {
        val me = owner("usage-by-model")
        record(me.uuid, model = "gpt-image-2")
        record(me.uuid, model = "gpt-image-2")
        record(me.uuid, model = "gpt-image-2.5-flare")

        mockMvc.get("/api/usage") { header("Authorization", "Bearer ${me.token}") }
            .andExpect {
                status { isOk() }
                jsonPath("$.byModel.length()") { value(2) }
                jsonPath("$.byModel[0].model") { value("gpt-image-2") }
                jsonPath("$.byModel[0].calls") { value(2) }
                jsonPath("$.byModel[1].model") { value("gpt-image-2.5-flare") }
                jsonPath("$.byModel[1].calls") { value(1) }
            }
    }

    /** 소유자는 요청이 아니라 토큰에서 나온다. 남의 지출은 조회할 방법 자체가 없어야 한다. */
    @Test
    fun `남의 지출은 보이지 않는다`() {
        val me = owner("usage-mine")
        val other = owner("usage-other")
        record(other.uuid)

        mockMvc.get("/api/usage") { header("Authorization", "Bearer ${me.token}") }
            .andExpect {
                status { isOk() }
                jsonPath("$.calls") { value(0) }
            }
    }

    @Test
    fun `구간 밖의 지출은 세지 않는다`() {
        val me = owner("usage-window")
        val now = Instant.now()
        record(me.uuid, calledAt = now.minus(40, ChronoUnit.DAYS))
        record(me.uuid, calledAt = now.minus(1, ChronoUnit.HOURS))

        mockMvc.get("/api/usage") {
            header("Authorization", "Bearer ${me.token}")
            param("from", now.minus(7, ChronoUnit.DAYS).toString())
            param("to", now.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.calls") { value(1) }
        }
    }

    /** 0 건인 것과 물음이 말이 안 되는 것은 다르다. */
    @Test
    fun `거꾸로 된 구간은 거절한다`() {
        val me = owner("usage-bad-range")
        val now = Instant.now()

        mockMvc.get("/api/usage") {
            header("Authorization", "Bearer ${me.token}")
            param("from", now.toString())
            param("to", now.minus(1, ChronoUnit.DAYS).toString())
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `로그인하지 않으면 볼 수 없다`() {
        mockMvc.get("/api/usage").andExpect { status { isUnauthorized() } }
    }
}
