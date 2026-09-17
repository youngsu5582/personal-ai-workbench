package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.generation.application.GenerationCommand
import dev.joyson.aiworkbench.generation.application.GenerationJobSubmitter
import dev.joyson.aiworkbench.generation.application.GenerationJobWriter
import dev.joyson.aiworkbench.generation.application.UnsupportedModelException
import dev.joyson.aiworkbench.generation.domain.option.AspectRatio
import dev.joyson.aiworkbench.generation.domain.option.ImageSize
import dev.joyson.aiworkbench.generation.domain.option.Resolution
import dev.joyson.aiworkbench.generation.domain.option.TextToImageOption
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.provider.ProviderRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 접수 여부를 판단하는 자리다. 쓰기가 잘 되는지는 [GenerationJobWriterTest] 가 본다.
 *
 * 앞으로 크레딧 차감·요금제 한도가 들어올 곳이라, 그 규칙들의 테스트도 여기 쌓인다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(
    GenerationJobSubmitter::class,
    GenerationJobWriter::class,
    GenerationJobSubmitterTest.FakeProviderConfig::class,
)
class GenerationJobSubmitterTest @Autowired constructor(
    private val submitter: GenerationJobSubmitter,
    private val jobRepository: GenerationJobRepository,
    private val taskRepository: GenerationJobTaskRepository,
) {

    @TestConfiguration
    class FakeProviderConfig {
        @Bean
        fun providerRegistry(): ProviderRegistry = ProviderRegistry(
            listOf(
                object : ExternalApiProvider {
                    override val name = "fake"
                    override fun supports(model: String) = model == MODEL
                    override fun generate(model: String, request: ExternalApiGenerateRequest) =
                        ExternalApiGenerateResponse(result = emptyList())
                },
            ),
        )
    }

    private fun command(model: String = MODEL, taskCount: Int = 2) = GenerationCommand(
        option = TextToImageOption("고양이", ImageSize.ByRatio(AspectRatio.ONE_ONE, Resolution.ONE_K)),
        model = model,
        taskCount = taskCount,
    )

    @Test
    fun `다룰 수 있는 모델이면 접수하고 Task 까지 만든다`() {
        val view = submitter.submit(userId = 1L, command = command())

        assertEquals(1, jobRepository.count())
        assertEquals(2, taskRepository.count())
        assertEquals(2, view.progress.total)
    }

    /**
     * 컨트롤러가 아니라 여기서 거른다.
     * 호출 경로가 늘어나면 호출자마다 같은 검사를 기억해야 하고, 한 곳만 빠뜨려도 조용히 통과한다.
     */
    @Test
    fun `다룰 Provider 가 없는 모델은 접수되지 않는다`() {
        assertFailsWith<UnsupportedModelException> {
            submitter.submit(userId = 1L, command = command(model = "존재하지-않는-모델"))
        }

        // 검증이 기록보다 먼저라 흔적이 남지 않는다.
        assertEquals(0, jobRepository.count())
        assertEquals(0, taskRepository.count())
    }

    private companion object {
        const val MODEL = "fake-image-1"
    }
}
