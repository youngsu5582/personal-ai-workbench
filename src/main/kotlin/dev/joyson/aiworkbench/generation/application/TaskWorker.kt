package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.Sha256
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobRepository
import dev.joyson.aiworkbench.generation.infrastructure.GenerationJobTaskRepository
import dev.joyson.aiworkbench.generation.infrastructure.ProviderRequestFactory
import dev.joyson.aiworkbench.generation.infrastructure.StorageKeys
import dev.joyson.aiworkbench.provider.ExternalApiException
import dev.joyson.aiworkbench.provider.ExternalApiGenerateResponse
import dev.joyson.aiworkbench.provider.ProviderRegistry
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.FileStorageException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Task 하나를 끝까지 처리한다 — 번역 → Provider 호출 → 보관 → 기록.
 *
 * **트리거를 모른다.** 지금은 폴러가 부르고 나중에 메시지 컨슈머가 불러도 이 클래스는 그대로다.
 * 그래서 큐를 바꾸는 일이 "누가 `process` 를 부르는가" 한 줄로 끝난다.
 *
 * **트랜잭션을 걸지 않는다.** Provider 호출이 수십 초라 그 동안 커넥션을 붙잡으면 안 된다.
 * 상태를 적는 짧은 순간만 [TaskStateWriter] 가 연다.
 */
@Component
class TaskWorker(
    private val taskRepository: GenerationJobTaskRepository,
    private val jobRepository: GenerationJobRepository,
    private val providerRegistry: ProviderRegistry,
    private val requestFactory: ProviderRequestFactory,
    private val imageSourceResolver: ImageSourceResolver,
    private val fileStorage: FileStorage,
    private val stateWriter: TaskStateWriter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun process(taskId: Long) {
        val task = taskRepository.findById(taskId).orElse(null) ?: return
        val job = jobRepository.findById(task.jobId).orElse(null) ?: run {
            // Task 는 있는데 Job 이 없는 건 설명되지 않는 상태다. 다시 해도 같으므로 닫는다.
            stateWriter.fail(taskId, "job 을 찾을 수 없다: ${task.jobId}", retryable = false)
            return
        }

        // 접수 때 확인했더라도 그 사이 설정이 바뀌어 Provider 가 사라졌을 수 있다.
        val provider = providerRegistry.find(job.model) ?: run {
            stateWriter.fail(taskId, "다룰 수 있는 Provider 가 없다: ${job.model}", retryable = false)
            return
        }

        log.info("task 를 처리한다. uuid={} model={} 시도={}", task.uuid, job.model, task.attemptCount)

        val startedAt = System.nanoTime()
        val stored = try {
            // 고칠 이미지가 있으면 여기서 읽는다. 접수 때 확인했더라도 그 사이 지워졌을 수 있고,
            // 어차피 바이트가 필요하므로 조회가 헛일이 아니다.
            // 재시도마다 다시 읽는 대가를 치르지만, 4k 이미지를 워커가 들고 있는 것보다 낫다.
            val images = imageSourceResolver.resolve(job.ownerUserUuid, job.option)
            val response = provider.generate(job.model, requestFactory.from(job.option, images))
            store(job, response)
        } catch (e: ImageSourceUnavailableException) {
            stateWriter.fail(taskId, e.message ?: "입력 이미지를 읽지 못했다", e.retryable)
            return
        } catch (e: ExternalApiException) {
            stateWriter.fail(taskId, e.message ?: "Provider 호출이 실패했다", e.retryable)
            return
        } catch (e: FileStorageException) {
            stateWriter.fail(taskId, e.message ?: "결과를 보관하지 못했다", e.retryable)
            return
        }

        stateWriter.succeed(taskId, stored)
        log.info(
            "task 처리를 완료했다. uuid={} 소요={}ms 파일={}",
            task.uuid,
            (System.nanoTime() - startedAt) / 1_000_000,
            stored.map { it.storageKey },
        )
    }

    /**
     * 바이트를 보관소에 쓰고 기록할 정보만 남긴다.
     *
     * 상태 기록보다 **먼저** 한다. 순서를 뒤집으면 SUCCEEDED 인데 파일이 없는 행이 생길 수 있고,
     * 그건 목록에서 깨진 이미지로만 드러난다. 반대 순서의 실패(파일은 있는데 기록이 없음)는
     * 아무도 가리키지 않는 파일 하나로 끝난다 — 눈에 띄지 않고, 나중에 정리 작업이 걷어낸다.
     */
    private fun store(
        job: GenerationJob,
        response: ExternalApiGenerateResponse,
    ): List<StoredFile> = response.result.map { result ->
        // 자리는 내용이 정한다 — 같은 바이트면 같은 키라 이 보관이 멱등하다. 다시 돌려도 고아가 안 생긴다.
        val key = StorageKeys.generatedFile(
            ownerUuid = job.ownerUserUuid,
            digest = Sha256.of(result.image),
            mimeType = result.metadata.mimeType,
        )
        fileStorage.put(key, result.image, result.metadata.mimeType)

        // 행의 식별자는 따로 둔다. digest 에서 파생시키면 같은 바이트를 가진 두 행이
        // uk_generated_files_uuid 에서 충돌한다 — 키는 내용 주소, 행 식별자는 랜덤이다.
        val fileUuid = UUID.randomUUID()

        StoredFile(
            uuid = fileUuid,
            storageKey = key,
            metadata = FileMetadata(
                width = result.metadata.width,
                height = result.metadata.height,
                mimeType = result.metadata.mimeType,
                fileSize = result.metadata.fileSize,
            ),
        )
    }
}
