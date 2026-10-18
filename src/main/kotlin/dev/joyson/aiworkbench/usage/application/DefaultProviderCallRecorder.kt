package dev.joyson.aiworkbench.usage.application

import dev.joyson.aiworkbench.usage.FailureKind
import dev.joyson.aiworkbench.usage.ProviderCallRecorder
import dev.joyson.aiworkbench.usage.RecordProviderCallCommand
import dev.joyson.aiworkbench.usage.domain.CalculatedCost
import dev.joyson.aiworkbench.usage.domain.ProviderCall
import dev.joyson.aiworkbench.usage.domain.ReportedCost
import dev.joyson.aiworkbench.usage.domain.ProviderCallRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID

/**
 * 호출 한 건을 적는다.
 *
 * **던지지 않는 것이 이 클래스의 설계 목표다.** 부르는 쪽은 이미 Provider 에 돈을 쓴 뒤고,
 * 여기서 예외가 나 그쪽 작업이 실패 처리되면 재시도가 돈을 또 쓴다.
 * 원장 한 줄을 잃는 것보다 나쁘다. 그래서
 *
 *   - 외부 I/O 가 없다. 순수 INSERT 하나다
 *   - 다른 모듈을 가리키는 FK 가 없다 (FK 는 곧 실패할 수 있는 제약이다)
 *   - 길이를 넘는 문자열은 잘라서 넣는다 — 사유가 길다는 이유로 기록을 통째로 잃지 않는다
 *   - 호출을 세다가 난 예외는 삼킨다 — 트랜잭션 안이라 올라가면 INSERT 까지 되돌려진다
 *
 * 자르는 일이 여기 있는 이유: 저장되는 모양은 저장하는 모듈이 소유한다.
 * 부르는 쪽이 우리 컬럼 폭을 알아야 한다면 그건 경계가 새는 것이다.
 *
 * 세는 일이 여기 있는 이유: 모든 Provider 호출이 지나는 길이 여기 하나고, 지연·성공·실패 종류가
 * 이미 커맨드에 있다. 원장은 "이 호출 하나" 를, metric 은 "얼마나 자주·얼마나 오래" 를 답한다.
 */
@Service
class DefaultProviderCallRecorder(
    private val repository: ProviderCallRepository,
    private val meterRegistry: MeterRegistry,
) : ProviderCallRecorder {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun record(command: RecordProviderCallCommand): UUID {
        // INSERT 보다 먼저 센다. 원장 기록이 실패해도 호출은 일어났다.
        observe(command)

        val reported = ReportedCost.of(command.reportedAmount, command.reportedUnit)

        return repository.save(
            ProviderCall(
                taskUuid = command.taskUuid,
                jobUuid = command.jobUuid,
                ownerUserUuid = command.ownerUserUuid,
                provider = command.provider.take(COLUMN_LENGTH),
                model = command.model.take(COLUMN_LENGTH),
                // JSON 이라 길이 제약이 없다 — 자를 이유가 없어졌다.
                request = command.request,
                succeeded = command.succeeded,
                failureReason = command.failureReason?.take(COLUMN_LENGTH),
                failureKind = command.failureKind,
                // 음수 지연은 나올 수 없지만, 시계가 뒤로 가는 환경에서도 제약에 걸려 죽지 않게 한다.
                latencyMs = command.latencyMs.coerceAtLeast(0),
                usageRaw = command.usageRaw,
                reported = reported,
                // Provider 가 달러로 답했으면 그것이 곧 비용이다 — 계산이 아니라 같은 사실을 제자리에
                // 옮기는 것이라 단가표가 없는 지금도 채워진다. 크레딧은 그 Provider 의 크레딧 단가를
                // 알아야 달러가 되므로 asUsd 가 답하지 않는다.
                cost = reported?.asUsd()?.let(CalculatedCost::reported),
                calledAt = command.calledAt,
            ),
        ).uuid
    }

    /**
     * tag 키는 성공·실패 모두 같은 넷이다. Prometheus 는 이름이 같으면 tag 키 구성도 같기를 요구한다 —
     * 그래서 성공에도 `failure_kind` 를 `none` 으로 채운다.
     */
    private fun observe(command: RecordProviderCallCommand) {
        try {
            Timer.builder(PROVIDER_CALL)
                .description(PROVIDER_CALL_DESCRIPTION)
                .tag("provider", command.provider)
                .tag("model", command.model)
                .tag("outcome", if (command.succeeded) "success" else "failure")
                .tag("failure_kind", command.failureKind.metricTag())
                .serviceLevelObjectives(*PROVIDER_CALL_BUCKETS)
                .register(meterRegistry)
                .record(Duration.ofMillis(command.latencyMs.coerceAtLeast(0).toLong()))
        } catch (e: Exception) {
            log.warn("Provider 호출을 세지 못했다. 원장 기록은 계속한다. provider={} model={}", command.provider, command.model, e)
        }
    }

    /** 대시보드와 알림이 이 값에 묶인다. enum 이름을 바꿔도 시계열이 끊기지 않게 표기를 따로 둔다. */
    private fun FailureKind?.metricTag(): String = when (this) {
        null -> "none"
        FailureKind.REJECTED -> "rejected"
        FailureKind.THROTTLED -> "throttled"
        FailureKind.PROVIDER_ERROR -> "provider_error"
        FailureKind.NO_RESPONSE -> "no_response"
        FailureKind.UNKNOWN -> "unknown"
    }

    private companion object {
        const val COLUMN_LENGTH = 255

        const val PROVIDER_CALL = "workbench.provider.call"

        /** Prometheus 의 `# HELP` 줄이 된다. 이름만 봐서는 보관 시간이 들어 있는지 알 수 없다. */
        const val PROVIDER_CALL_DESCRIPTION =
            "Provider 호출 한 번의 길이. 보관 시간은 들어 있지 않다. outcome·failure_kind 로 성공률과 실패 종류를 본다"

        /**
         * 구간을 주지 않으면 횟수·합·최대만 나가 분포(p95 등)를 볼 수 없다.
         * Provider 호출은 건당 수십 초라 그 범위에 맞춘 여섯 개만 둔다 — 시계열 수가 구간 수만큼 는다.
         */
        val PROVIDER_CALL_BUCKETS: Array<Duration> =
            arrayOf(5L, 10L, 30L, 60L, 120L, 300L).map(Duration::ofSeconds).toTypedArray()
    }
}
