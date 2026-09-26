package dev.joyson.aiworkbench.usage.application

import dev.joyson.aiworkbench.usage.ProviderCallRecorder
import dev.joyson.aiworkbench.usage.RecordProviderCallCommand
import dev.joyson.aiworkbench.usage.domain.CalculatedCost
import dev.joyson.aiworkbench.usage.domain.ProviderCall
import dev.joyson.aiworkbench.usage.domain.ReportedCost
import dev.joyson.aiworkbench.usage.infrastructure.ProviderCallRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
 *
 * 자르는 일이 여기 있는 이유: 저장되는 모양은 저장하는 모듈이 소유한다.
 * 부르는 쪽이 우리 컬럼 폭을 알아야 한다면 그건 경계가 새는 것이다.
 */
@Service
class DefaultProviderCallRecorder(
    private val repository: ProviderCallRepository,
) : ProviderCallRecorder {

    @Transactional
    override fun record(command: RecordProviderCallCommand): UUID {
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

    private companion object {
        const val COLUMN_LENGTH = 255
    }
}
