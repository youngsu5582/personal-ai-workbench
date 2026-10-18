package dev.joyson.aiworkbench.usage.application

import dev.joyson.aiworkbench.usage.domain.ModelPriceBook
import dev.joyson.aiworkbench.usage.domain.ModelPriceBookRepository
import dev.joyson.aiworkbench.usage.domain.ProviderCall
import dev.joyson.aiworkbench.usage.domain.ProviderCallCost
import dev.joyson.aiworkbench.usage.domain.ProviderCallCostRepository
import dev.joyson.aiworkbench.usage.domain.ProviderCallRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 호출 한 건의 비용을 정한다. 폴러가 부른다.
 *
 * **계산은 여기서 하지 않는다** — [ModelPriceBook.price] 가 한다. 여기는 어느 표를 쓸지 고르고,
 * 결과를 행으로 남기고, 트랜잭션을 맡는다. 그래서 부르는 쪽이 폴러에서 이벤트로 바뀌어도
 * 이 클래스와 계산은 그대로다. 입력이 호출 id 하나인 것도 같은 이유다 — 무엇이 부르든 넘길 수 있는 가장 작은 값이다.
 *
 * 비용을 정하지 못해도 행을 남긴다(usd = null). 그 행이 "이 호출은 봤다" 는 표시라,
 * 없으면 폴러가 미등록 모델의 호출을 주기마다 다시 집는다.
 *
 * 기록 경로(`ProviderCallRecorder`)에서 하지 않는 이유: 그 경로는 Provider 에 돈을 쓴 뒤에 돌아서,
 * 일을 더할수록 원장 한 줄을 잃을 위험이 커진다.
 */
@Component
class ProviderCallPricingWorker(
    private val calls: ProviderCallRepository,
    private val priceBooks: ModelPriceBookRepository,
    private val costs: ProviderCallCostRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 아직 비용 행이 없는 호출. 오래된 것부터 [limit] 건. */
    @Transactional(readOnly = true)
    fun unpriced(limit: Int): List<Long> = calls.findUnpricedIds(PageRequest.of(0, limit))

    /**
     * 이미 비용 행이 있으면 아무것도 하지 않는다. 같은 호출을 두 번 넘겨받아도 행은 하나다.
     *
     * 둘이 동시에 들어오면 이 확인을 함께 지나칠 수 있다. 그때는 두 번째 INSERT 가 PK 에 막힌다 —
     * 선점 상태를 두지 않는 이유는 계산이 외부 호출 없이 같은 값을 내서, 두 번 돌아도 잃는 것이 없기 때문이다.
     */
    @Transactional
    fun price(callId: Long) {
        if (costs.existsById(callId)) return
        val call = calls.findById(callId) ?: return

        // 단가표는 호출 시각(calledAt)으로 고른다. 여기 넘기는 것은 비용을 붙인 시각이다 — 폴러가 늦은 만큼 둘이 벌어진다.
        costs.save(costOf(call, Instant.now()))
    }

    private fun costOf(call: ProviderCall, pricedAt: Instant): ProviderCallCost {
        // Provider 가 달러로 답했으면 그것이 곧 비용이다. 계산이 아니라 같은 사실을 옮기는 것이라 단가표보다 앞선다.
        // 크레딧은 그 Provider 의 크레딧 단가를 알아야 달러가 되므로 asUsd 가 답하지 않는다.
        call.reported?.asUsd()?.let { return ProviderCallCost.reported(call, it, pricedAt) }

        val usage = call.usageRaw
        if (usage.isNullOrEmpty()) {
            // 실패한 호출에 사용량이 없는 것은 정상이다. 성공했는데 없으면 그 Provider 의 호출은 비용을 영영 못 낸다.
            if (call.succeeded) {
                log.warn("사용량이 없어 비용을 정하지 못했다. call={} model={}", call.uuid, call.model)
            }
            return ProviderCallCost.unknown(call, pricedAt)
        }

        val book = bookFor(call) ?: return ProviderCallCost.unknown(call, pricedAt)
        val usd = book.price(usage)
        if (usd == null) {
            // 경로 철자가 틀렸거나 Provider 가 응답 모양을 바꿨다. 이 모델의 호출이 전부 여기로 온다.
            log.warn(
                "단가표가 가리키는 칸이 사용량에 없다. call={} model={} 표={} 단가 칸={} 사용량 칸={}",
                call.uuid, call.model, book.id, book.rates.usdPerMillion.keys, usage.keys,
            )
            return ProviderCallCost.unknown(call, pricedAt)
        }

        return ProviderCallCost.derived(call, usd, book, pricedAt)
    }

    /**
     * 호출 시각에 적용되는 표. 겹친 기간은 DB 가 막지 않으므로 둘 이상 나올 수 있다 —
     * 그때 하나를 골라 적으면 틀린 단가가 실측처럼 남으므로 고르지 않는다.
     */
    private fun bookFor(call: ProviderCall): ModelPriceBook? {
        val books = priceBooks.findEffective(call.provider, call.model, call.calledAt)
        return when (books.size) {
            1 -> books.single()
            0 -> {
                log.warn(
                    "맞는 단가표가 없어 비용을 정하지 못했다. call={} provider={} model={} 호출 시각={}",
                    call.uuid, call.provider, call.model, call.calledAt,
                )
                null
            }
            else -> {
                log.warn(
                    "단가표 기간이 겹쳐 비용을 정하지 못했다. call={} model={} 표={}",
                    call.uuid, call.model, books.map { it.id },
                )
                null
            }
        }
    }
}
