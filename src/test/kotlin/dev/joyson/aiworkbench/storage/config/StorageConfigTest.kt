package dev.joyson.aiworkbench.storage.config

import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import dev.joyson.aiworkbench.storage.local.LocalFileStorage
import dev.joyson.aiworkbench.storage.s3.S3FileStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * 어느 보관소가 뜨는지를 확인한다.
 *
 * `ApplicationContextRunner` 를 쓴다 — 스프링 테스트 컨텍스트 캐시에 잡히지 않아
 * 설정 조합을 몇 개 만들어도 전체 테스트가 느려지지 않는다. `OpenAIClientConfigTest` 와 같은 모양이다.
 */
class StorageConfigTest {

    @TempDir
    lateinit var tempDir: Path

    private fun localRoot() = tempDir.resolve("assets")

    /**
     * 로컬 보관소의 경로를 **항상** 임시 디렉토리로 돌린다.
     *
     * 기본값(`./var/assets`)을 그대로 두면 `LocalFileStorage` 의 init 이 **프로젝트 루트에**
     * 디렉토리를 판다. 테스트가 작업 디렉토리에 흔적을 남기는 것은 그 자체로 버그다.
     *
     * 개별 테스트가 아니라 여기에 두는 이유는, 한 곳만 고치면 다음 테스트가 또 잊기 때문이다.
     * `val` 이 아니라 `get()` 인 것은 [tempDir] 주입이 인스턴스 생성 **뒤에** 일어나서다.
     */
    private val runner: ApplicationContextRunner
        get() = ApplicationContextRunner()
            .withUserConfiguration(StorageConfig::class.java)
            .withPropertyValues("workbench.storage.local.root=${localRoot()}")

    @Test
    fun `아무것도 정하지 않으면 로컬이다`() {
        runner.run { context ->
            assertThat(context).hasSingleBean(FileStorage::class.java)
            assertThat(context.getBean(FileStorage::class.java)).isInstanceOf(LocalFileStorage::class.java)
        }
    }

    @Test
    fun `s3 를 고르면 S3 보관소가 뜬다`() {
        runner.withPropertyValues(
            "workbench.storage.provider=s3",
            "workbench.storage.s3.bucket=workbench",
        ).run { context ->
            assertThat(context.getBean(FileStorage::class.java)).isInstanceOf(S3FileStorage::class.java)
        }
    }

    @Test
    fun `빈 문자열은 기동에서 잡힌다`() {
        // yaml 의 `"${STORAGE_PROVIDER:}"` 가 만드는 상태다. 이걸 넘기면 보관소 없이 서비스가 뜬다.
        runner.withPropertyValues("workbench.storage.provider=").run { context ->
            assertThat(context).hasFailed()
        }
    }

    @Test
    fun `모르는 이름은 가능한 목록과 함께 거절한다`() {
        runner.withPropertyValues("workbench.storage.provider=s4").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure)
                .rootCause()
                .hasMessageContaining("s4")
                .hasMessageContaining("local")
                .hasMessageContaining("s3")
        }
    }

    @Test
    fun `s3 를 고르면 bucket 이 없을 때 기동에서 잡힌다`() {
        runner.withPropertyValues("workbench.storage.provider=s3").run { context ->
            assertThat(context).hasFailed()
        }
    }

    @Test
    fun `로컬은 주소를 발급할 수 없어 발급자가 없다`() {
        // 포트를 나눠 둔 이유가 이것이다 — 못 하는 구현이 있을 때 "발급자가 없다" 로 표현된다.
        //
        // `doesNotHaveBean` 으로 보지 않는다. `@Bean` 이 null 을 돌려주면 스프링은 NullBean 이라는
        // 자리를 남겨서 "타입은 있는데 값이 없는" 상태가 된다. 소비자가 실제로 받는 것을 확인한다.
        runner.run { context ->
            assertThat(context.getBeanProvider(PresignedUrlIssuer::class.java).getIfAvailable()).isNull()
        }
    }

    @Test
    fun `s3 를 고르면 발급자가 함께 선다`() {
        runner.withPropertyValues(
            "workbench.storage.provider=s3",
            "workbench.storage.s3.bucket=workbench",
        ).run { context ->
            assertThat(context).hasSingleBean(PresignedUrlIssuer::class.java)
        }
    }

    @Test
    fun `고르지 않은 구현은 아예 만들지 않는다`() {
        // 팩토리로 감싼 이유가 이것이다. 로컬 구현은 만들어지는 순간 디렉토리를 판다 —
        // s3 로 띄운 환경에 빈 assets 디렉토리가 생기면 설정이 거짓말을 하는 것이다.
        runner.withPropertyValues(
            "workbench.storage.provider=s3",
            "workbench.storage.s3.bucket=workbench",
        ).run { _ ->
            assertThat(localRoot().exists()).isFalse()
        }

        runner.withPropertyValues("workbench.storage.provider=local").run { _ ->
            assertThat(localRoot().exists()).isTrue()
        }
    }
}
