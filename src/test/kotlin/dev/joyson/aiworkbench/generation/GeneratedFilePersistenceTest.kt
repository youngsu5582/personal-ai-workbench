package dev.joyson.aiworkbench.generation

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import dev.joyson.aiworkbench.storage.Sha256
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 결과물 메타는 종류마다 모양이 달라 JSON 한 칸에 판별자와 함께 담는다.
 * 판별자가 생기기 전의 행에는 `type` 이 없다 — 그 행을 고치지 않고 읽는 것이 이 매핑의 성립 조건이다.
 *
 * 저장은 Hibernate 의 JSON 매퍼를 지난다. 애플리케이션 매퍼로 본 표기와 다를 수 있어 실제 행으로 본다.
 */
class GeneratedFilePersistenceTest @Autowired constructor(
    private val em: EntityManager,
    private val fileRepository: GeneratedFileTestRepository,
) : IntegrationTest() {

    @Test
    fun `판별자 없는 옛 행은 이미지로 읽힌다`() {
        em.createNativeQuery(
            """
            insert into generated_files (sha256, task_id, storage_key, metadata, created_at)
            values (:sha256, :taskId, 'old-key',
                    '{"width":1024,"height":768,"mimeType":"image/png","fileSize":10}'::jsonb, current_timestamp)
            """,
        ).setParameter("sha256", SHA256).setParameter("taskId", TASK_ID).executeUpdate()
        em.flush(); em.clear()

        val metadata = fileRepository.findAllByTaskId(TASK_ID).single().metadata

        assertEquals(FileMetadata.Image(width = 1024, height = 768, mimeType = "image/png", fileSize = 10), metadata)
    }

    @Test
    fun `이미지와 오디오는 판별자와 함께 적히고 원래 종류로 읽힌다`() {
        val image = FileMetadata.Image(width = 1024, height = 768, mimeType = "image/png", fileSize = 10)
        val audio = FileMetadata.Audio(durationSeconds = 8.48, mimeType = "audio/wav", fileSize = 414_764)
        listOf(image, audio).forEach {
            fileRepository.save(GeneratedFile(sha256 = SHA256, taskId = TASK_ID, storageKey = "key", metadata = it))
        }
        em.flush(); em.clear()

        val types = em.createNativeQuery("select metadata->>'type' from generated_files where task_id = :taskId order by id")
            .setParameter("taskId", TASK_ID)
            .resultList
        assertEquals(listOf<Any?>("image", "audio"), types)
        assertEquals(listOf(image, audio), fileRepository.findAllByTaskId(TASK_ID).sortedBy { it.id }.map { it.metadata })
    }

    private companion object {
        val SHA256 = Sha256.of("결과물".toByteArray()).hex

        /** `generated_files.task_id` 에는 FK 가 없어 Task 행 없이 넣을 수 있다. */
        const val TASK_ID = 9_001L
    }
}
