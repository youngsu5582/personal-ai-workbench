package dev.joyson.aiworkbench.upload

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.storage.BlobKey
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.PresignedUploadIssuer
import dev.joyson.aiworkbench.storage.Sha256
import dev.joyson.aiworkbench.upload.application.DefaultUploadedFiles
import dev.joyson.aiworkbench.upload.application.UploadService
import dev.joyson.aiworkbench.upload.application.UploadedFileWriter
import dev.joyson.aiworkbench.upload.domain.UploadSpec
import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UploadServiceTest @Autowired constructor(
    private val repository: UploadedFileRepository,
    private val writer: UploadedFileWriter,
    private val storage: FileStorage,
    private val files: DefaultUploadedFiles,
    private val jdbc: JdbcTemplate,
) : IntegrationTest() {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
    private val digest = Sha256.of(png)
    private val spec = UploadSpec("image/png", png.size.toLong(), digest)
    private val service by lazy {
        UploadService(
            PresignedUploadIssuer { key, _, _, _ -> URI.create("https://storage.test/$key") },
            repository, writer, files,
        )
    }

    @Test
    fun `다른 사용자는 같은 해시라도 별도 UUID 와 객체를 가진다`() {
        val first = assertNotNull(service.issue(UUID.randomUUID(), spec))
        val second = assertNotNull(service.issue(UUID.randomUUID(), spec))
        assertNotEquals(first.uuid, second.uuid)
        assertNotEquals(first.url, second.url)
    }

    @Test
    fun `READY 중복은 보관소를 읽거나 쓰기 주소를 발급하지 않는다`() {
        val owner = UUID.randomUUID()
        val issued = assertNotNull(service.issue(owner, spec))
        val file = assertNotNull(repository.findByUuidAndOwnerUserUuid(issued.uuid, owner))
        storage.put(file.storageKey, png, "image/png")
        files.findUsable(file.uuid, owner)

        val noRead = object : FileStorage by storage {
            override fun readRange(key: String, range: LongRange): Nothing = error("READY 를 다시 읽었다")
        }
        val noSign = PresignedUploadIssuer { _, _, _, _ -> error("READY 에 쓰기 권한을 발급했다") }
        val readyService = UploadService(noSign, repository, writer, DefaultUploadedFiles(repository, writer, noRead))
        val reused = assertNotNull(readyService.issue(owner, spec))
        assertEquals(issued.uuid, reused.uuid)
        assertNull(reused.url)
    }

    @Test
    fun `형식 정정 뒤에는 옛 PENDING 판정과 옛 정정 요청이 새 상태를 덮지 못한다`() {
        val owner = UUID.randomUUID()
        val wrongSpec = UploadSpec("image/jpeg", png.size.toLong(), digest)
        val issued = assertNotNull(service.issue(owner, wrongSpec))
        val oldPending = assertNotNull(repository.findByUuidAndOwnerUserUuid(issued.uuid, owner))
        assertTrue(writer.settle(oldPending, UploadStatus.REJECTED))
        val rejected = assertNotNull(repository.findByUuidAndOwnerUserUuid(issued.uuid, owner))
        assertTrue(writer.reopen(rejected, BlobKey.uploaded(owner, digest, "image/png").value, spec))
        val corrected = assertNotNull(repository.findByUuidAndOwnerUserUuid(issued.uuid, owner))

        assertFalse(writer.settle(oldPending, UploadStatus.REJECTED))
        assertFalse(writer.reopen(rejected, "stale-key", wrongSpec))
        assertEquals("image/png", corrected.metadata.mimeType)
        assertEquals(UploadStatus.PENDING, repository.findByUuidAndOwnerUserUuid(issued.uuid, owner)?.status)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `동시 최초 발급도 한 행과 한 UUID 를 공유한다`() {
        val owner = UUID.randomUUID()
        val key = BlobKey.uploaded(owner, digest, "image/png").value
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = List(4) {
                pool.submit<UUID> {
                    assertTrue(gate.await(10, TimeUnit.SECONDS))
                    writer.createOrFind(owner, key, spec).uuid
                }
            }
            gate.countDown()
            val uuids = futures.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, uuids.toSet().size)
            assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM uploaded_files WHERE owner_user_uuid = ?", Long::class.java, owner,
            ))
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(10, TimeUnit.SECONDS)
            jdbc.update("DELETE FROM uploaded_files WHERE owner_user_uuid = ?", owner)
        }
    }
}
