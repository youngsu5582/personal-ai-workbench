package dev.joyson.aiworkbench.upload

import dev.joyson.aiworkbench.IntegrationTest
import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.PartialContent
import dev.joyson.aiworkbench.storage.PresignedUrlIssuer
import dev.joyson.aiworkbench.upload.application.DefaultUploadedFiles
import dev.joyson.aiworkbench.upload.application.UploadedFileWriter
import dev.joyson.aiworkbench.upload.domain.UploadStatus
import dev.joyson.aiworkbench.upload.domain.UploadedFile
import dev.joyson.aiworkbench.upload.domain.UploadedFileMetadata
import dev.joyson.aiworkbench.upload.domain.UploadedFileRepository
import org.springframework.beans.factory.annotation.Autowired
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 보관소는 공통 설정의 메모리 구현이다. 판정에 필요한 것은 앞부분과 크기뿐이라 S3 일 필요가 없다. */
class UploadedFilesTest @Autowired constructor(
    private val uploadedFiles: UploadedFiles,
    private val uploadedFileRepository: UploadedFileRepository,
    private val uploadedFileWriter: UploadedFileWriter,
    private val fileStorage: FileStorage,
) : IntegrationTest() {

    private val owner = UUID.randomUUID()
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52)

    private fun pending(mimeType: String = "image/png", fileSize: Long = png.size.toLong()): UploadedFile {
        val uuid = UUID.randomUUID()
        return uploadedFileRepository.save(
            UploadedFile(
                uuid = uuid,
                ownerUserUuid = owner,
                storageKey = "users/$owner/uploads/$uuid",
                metadata = UploadedFileMetadata(mimeType = mimeType, fileSize = fileSize),
            ),
        )
    }

    private fun statusOf(file: UploadedFile) =
        uploadedFileRepository.findByUuidAndOwnerUserUuid(file.uuid, owner)?.status

    @Test
    fun `선언한 형식과 크기로 올라왔으면 READY 로 남기고 쓸 수 있다`() {
        val file = pending()
        fileStorage.put(file.storageKey, png, "image/png")

        assertEquals(UsableUpload("image/png"), uploadedFiles.findUsable(file.uuid, owner))
        assertEquals(UploadStatus.READY, statusOf(file))
    }

    @Test
    fun `아직 안 올라왔으면 판정하지 않는다`() {
        val file = pending()

        assertNull(uploadedFiles.findUsable(file.uuid, owner))
        assertEquals(UploadStatus.PENDING, statusOf(file))
    }

    @Test
    fun `선언과 다른 형식이면 REJECTED 로 남기고 객체를 지운다`() {
        // zip 을 png 라고 선언했다.
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val file = pending(fileSize = zip.size.toLong())
        fileStorage.put(file.storageKey, zip, "image/png")

        assertNull(uploadedFiles.findUsable(file.uuid, owner))
        assertEquals(UploadStatus.REJECTED, statusOf(file))
        assertNull(fileStorage.read(file.storageKey))
    }

    @Test
    fun `선언과 크기가 다르면 REJECTED 다`() {
        val file = pending(fileSize = png.size + 1L)
        fileStorage.put(file.storageKey, png, "image/png")

        assertNull(uploadedFiles.findUsable(file.uuid, owner))
        assertEquals(UploadStatus.REJECTED, statusOf(file))
    }

    @Test
    fun `READY 는 보관소를 다시 보지 않는다`() {
        val file = pending()
        fileStorage.put(file.storageKey, png, "image/png")
        uploadedFiles.findUsable(file.uuid, owner)
        // 판정 뒤 객체가 사라져도 다시 확인하지 않는다.
        fileStorage.delete(file.storageKey)

        assertEquals(UsableUpload("image/png"), uploadedFiles.findUsable(file.uuid, owner))
    }

    @Test
    fun `남의 것은 없는 것과 같고 판정하지도 않는다`() {
        val file = pending()
        fileStorage.put(file.storageKey, png, "image/png")

        assertNull(uploadedFiles.findUsable(file.uuid, UUID.randomUUID()))
        assertEquals(UploadStatus.PENDING, statusOf(file))
    }

    @Test
    fun `READY 인 업로드에는 그 자리의 읽기 주소를 낸다`() {
        val file = pending()
        fileStorage.put(file.storageKey, png, "image/png")
        uploadedFiles.findUsable(file.uuid, owner)

        val url = withIssuer().issueReadUrl(file.uuid, owner, "${file.uuid}.png")

        assertEquals(URI.create("https://storage.test/${file.storageKey}?name=${file.uuid}.png"), url)
    }

    @Test
    fun `판정 전 업로드에는 읽기 주소를 내지 않는다`() {
        val file = pending()
        fileStorage.put(file.storageKey, png, "image/png")

        assertNull(withIssuer().issueReadUrl(file.uuid, owner, "${file.uuid}.png"))
        assertEquals(UploadStatus.PENDING, statusOf(file))
    }

    @Test
    fun `READY 인 업로드만 바이트를 내준다`() {
        val ready = pending()
        fileStorage.put(ready.storageKey, png, "image/png")
        uploadedFiles.findUsable(ready.uuid, owner)
        val notJudged = pending()
        fileStorage.put(notJudged.storageKey, png, "image/png")

        assertContentEquals(png, uploadedFiles.readContent(ready.uuid, owner))
        assertNull(uploadedFiles.readContent(notJudged.uuid, owner))
        assertNull(uploadedFiles.readContent(ready.uuid, UUID.randomUUID()))
    }

    /** 공통 설정은 로컬 보관소라 서명 발급자가 없다. 주소의 모양만 보면 되므로 가짜를 붙인다. */
    private fun withIssuer() = DefaultUploadedFiles(
        uploadedFileRepository,
        uploadedFileWriter,
        fileStorage,
        PresignedUrlIssuer { key, name -> URI.create("https://storage.test/$key?name=$name") },
    )

    @Test
    fun `판정하는 사이 다른 요청이 먼저 정하면 그 결과를 따르고 지우지 않는다`() {
        val file = pending()
        val deleted = mutableListOf<String>()
        // 앞부분을 읽는 순간 다른 요청이 REJECTED 로 정했다고 친다. 이쪽은 정상 PNG 를 본다.
        val racing = object : FileStorage by fileStorage {
            override fun readRange(key: String, range: LongRange): PartialContent {
                uploadedFileWriter.settle(file, UploadStatus.REJECTED)
                return PartialContent(png.copyOfRange(range.first.toInt(), range.last.toInt() + 1), png.size.toLong())
            }

            override fun delete(key: String) {
                deleted += key
            }
        }

        val result = DefaultUploadedFiles(uploadedFileRepository, uploadedFileWriter, racing).findUsable(file.uuid, owner)

        assertNull(result)
        assertEquals(UploadStatus.REJECTED, statusOf(file))
        assertTrue(deleted.isEmpty(), "이긴 쪽만 지운다")
    }
}
