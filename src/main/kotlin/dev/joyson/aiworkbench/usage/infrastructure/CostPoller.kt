package dev.joyson.aiworkbench.usage.infrastructure

import dev.joyson.aiworkbench.usage.application.ProviderCallPricingWorker
import dev.joyson.aiworkbench.usage.config.UsagePricingProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 비용 행이 없는 호출을 꺼내 [ProviderCallPricingWorker] 에 넘긴다.
 *
 * **"비용 행이 없음" 이 곧 할 일 목록이다.** 재시작해도 테이블이 그대로라 놓치는 호출이 없다.
 * `TaskPoller` 가 Task 테이블을 outbox 로 쓰는 것과 같은 판단이다.
 *
 * 기록이 커밋된 뒤 이벤트로 바로 계산하지 않는 이유: 인프로세스 이벤트는 재시작·장애에 유실되고,
 * 유실된 호출은 아무도 다시 집지 않는다. 같은 모듈 안이라 이벤트로 끊을 결합도 없다.
 * 대가는 비용이 폴 주기만큼 늦게 보이는 것이다.
 *
 * 선점하지 않는다. 계산이 외부 호출 없이 같은 값을 내서, 두 번 돌아도 두 번째 INSERT 가 PK 에 막힐 뿐이다.
 *
 * 얇게 유지한다. 일은 워커가 하고 여기는 꺼내서 넘기는 것만 한다.
 */
@Component
@ConditionalOnProperty(
    prefix = "workbench.usage.pricing",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class CostPoller(
    private val worker: ProviderCallPricingWorker,
    private val properties: UsagePricingProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 기본 60초에 근거는 없다. "이번 달 얼마 썼나" 를 보는 데 1분 늦는 것은 문제가 되지 않는다. */
    @Scheduled(
        fixedDelayString = $$"${workbench.usage.pricing.poll-interval-millis:60000}",
        initialDelayString = $$"${workbench.usage.pricing.initial-delay-millis:10000}",
    )
    fun poll() {
        val callIds = worker.unpriced(properties.batchSize)
        if (callIds.isEmpty()) return

        // 한 건씩 따로 트랜잭션이다. 하나가 터져도 나머지는 붙는다.
        val priced = callIds.count { callId ->
            try {
                worker.price(callId)
                true
            } catch (e: Exception) {
                // 비용 행이 생기지 않았으므로 다음 주기에 다시 집힌다.
                log.error("비용을 붙이지 못했다. 다음 주기에 다시 본다. callId={}", callId, e)
                false
            }
        }

        // 0 건인 주기는 남기지 않는다. 1분마다 한 줄이면 소음이다.
        log.info("비용을 붙였다. {}건 / 집은 것 {}건", priced, callIds.size)
    }
}
