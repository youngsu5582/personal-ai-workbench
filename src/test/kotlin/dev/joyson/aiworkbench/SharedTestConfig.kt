package dev.joyson.aiworkbench

import dev.joyson.aiworkbench.provider.ExternalApiGenerateRequest
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ExternalApiProvider
import dev.joyson.aiworkbench.storage.FileStorage
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 통합 테스트가 공유하는 바깥 세계.
 *
 * 여기에 담는 것은 **모든 테스트가 같은 값으로 써도 되는 것들**이다.
 * 테스트마다 다르게 세우면 설정이 갈리고, 설정이 갈리면 스프링 컨텍스트가 하나 더 뜬다.
 *
 * 나중에 MQ·Redis 가 붙어도 자리는 여기다 — 컨테이너를 하나 더 여는 것으로 끝나고,
 * 테스트 쪽은 아무것도 바뀌지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
class SharedTestConfig {

    /**
     * 테스트 환경에는 API 키가 없어 Provider 빈이 하나도 없다. 그러면 어떤 모델도 접수되지 않는다.
     *
     * 결과를 바꿔 가며 봐야 하는 테스트(워커 재시도 등)는 이 빈을 쓰지 않고 자기 것을 직접 만든다 —
     * 컨텍스트에 손대지 않으므로 컨텍스트가 늘지 않는다.
     */
    @Bean
    fun fakeProvider(): ExternalApiProvider = object : ExternalApiProvider {
        override val name = FAKE_PROVIDER
        override fun supports(model: String) = model == FAKE_MODEL
        override fun generate(model: String, request: ExternalApiGenerateRequest) =
            ExternalApiGenerateResponse(result = emptyList())
    }

    /** 테스트가 실제 디스크에 파일을 남기지 않게 한다. */
    @Bean
    @Primary
    fun inMemoryFileStorage(): FileStorage = object : FileStorage {
        private val written = mutableMapOf<String, ByteArray>()
        override fun put(key: String, content: ByteArray, contentType: String) {
            written[key] = content
        }

        override fun read(key: String): ByteArray? = written[key]
    }

    companion object {
        const val FAKE_PROVIDER = "fake"
        const val FAKE_MODEL = "fake-model"
    }
}
