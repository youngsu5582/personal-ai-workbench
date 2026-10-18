package dev.joyson.aiworkbench.usage

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.usage.application.ProviderCallPricingWorker
import dev.joyson.aiworkbench.usage.config.UsagePricingProperties
import dev.joyson.aiworkbench.usage.infrastructure.CostPoller
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 테스트 설정은 폴러를 끈다. 스케줄러 없이 한 주기를 직접 돌려 본다 —
 * 컨텍스트에 손대지 않으므로 컨텍스트가 늘지 않는다.
 */
class CostPollerTest @Autowired constructor(
    private val recorder: ProviderCallRecorder,
    private val worker: ProviderCallPricingWorker,
) : IntegrationTest() {

    private fun record() = recorder.record(
        RecordProviderCallCommand(
            taskUuid = UUID.randomUUID(),
            jobUuid = UUID.randomUUID(),
            ownerUserUuid = UUID.randomUUID(),
            provider = "openai",
            model = "gpt-image-2",
            succeeded = false,
            latencyMs = 1_000,
        ),
    )

    @Test
    fun `한 주기에 정한 수만큼만 붙이고 나머지는 다음 주기가 받는다`() {
        repeat(3) { record() }
        val poller = CostPoller(worker, UsagePricingProperties(batchSize = 2))

        poller.poll()
        assertEquals(1, worker.unpriced(100).size)

        poller.poll()
        assertEquals(0, worker.unpriced(100).size)
    }
}
