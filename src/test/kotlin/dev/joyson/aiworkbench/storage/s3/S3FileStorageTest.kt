package dev.joyson.aiworkbench.storage.s3

import dev.joyson.aiworkbench.storage.FileStorageException
import software.amazon.awssdk.services.s3.S3Client
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 진짜 S3 호환 서버(Garage)에 대고 확인한다.
 *
 * **스프링을 띄우지 않는다.** 이 클래스가 검증하는 것은 어댑터이지 배선이 아니고,
 * 컨텍스트를 하나 더 만들면 캐시에 잡혀 전체 테스트가 느려진다.
 *
 * 클라이언트를 손으로 조립하지 않고 [S3Clients.of] 를 그대로 부른다 —
 * path-style·체크섬·HTTP 클라이언트 설정이 검증 밖으로 빠지면 이 테스트는 초록인데 운영만 깨진다.
 *
 * 상대는 [GarageTestStorage] 가 띄운다 — 왜 Garage 인지도 거기 적혀 있다.
 */
class S3FileStorageTest {

    companion object {
        private val BUCKET = GarageTestStorage.BUCKET
        private val client: S3Client = S3Clients.of(GarageTestStorage.properties())
    }

    private val storage = S3FileStorage(client, BUCKET)

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    @Test
    fun `넣은 바이트를 그대로 읽는다`() {
        storage.put("users/a/blobs/ba/78/ba78.png", png, "image/png")

        assertContentEquals(png, storage.read("users/a/blobs/ba/78/ba78.png"))
    }

    @Test
    fun `없는 키는 예외가 아니라 null 이다`() {
        assertNull(storage.read("없는/키.png"))
    }

    @Test
    fun `같은 키에 다시 쓰면 덮인다`() {
        storage.put("overwrite.png", png, "image/png")
        storage.put("overwrite.png", byteArrayOf(1, 2, 3), "image/png")

        assertContentEquals(byteArrayOf(1, 2, 3), storage.read("overwrite.png"))
    }

    @Test
    fun `형식을 객체에 함께 싣는다`() {
        // 로컬 구현에는 이 개념이 없다(파일에 타입을 못 붙인다). 원격으로 옮겨야 비로소
        // 브라우저가 이미지로 열 수 있게 되므로, 실제로 실리는지 확인한다.
        storage.put("typed.png", png, "image/png")

        val head = client.headObject { it.bucket(BUCKET).key("typed.png") }
        assertEquals("image/png", head.contentType())
    }

    @Test
    fun `없는 버킷은 다시 해도 소용없다`() {
        val ex = runCatching { S3FileStorage(client, "없는-버킷-9999").put("a.png", png, "image/png") }
            .exceptionOrNull()

        assertTrue(ex is FileStorageException)
        // 4xx 다. 같은 요청을 다시 보내도 버킷은 여전히 없다.
        // 깨졌을 때 원인을 보려면 실제 예외가 필요하다 — 불리언만으로는 5xx 인지 연결 실패인지 알 수 없다.
        assertFalse(ex.retryable, "재시도 대상이 아니어야 한다. 실제 원인: ${ex.cause}")
        // 메시지만 로그와 DB 에 남는다. 여기에 원인이 없으면 설정 실수를 가려낼 수 없다.
        assertContains(ex.message!!, "NoSuchBucket")
    }

    @Test
    fun `없는 버킷은 본문이 커도 거절이 먼저 온다`() {
        // 서버는 헤더만 보고 거절하고 연결을 닫는다. 그때 본문을 보내고 있으면 거절 응답을 잃고
        // 연결 실패(재시도 대상)로 끝난다. 그래서 PUT 은 판정을 받은 뒤에만 본문을 보내야 한다.
        // SDK 는 1 MiB 부터만 Expect 헤더를 붙이므로 그 선의 양쪽을 본다. 이 테스트가 확실히 잡는 것은
        // HTTP 클라이언트를 JDK 것으로 되돌리는 회귀다(헤더가 있는 쪽에서 오류 코드가 사라진다).
        // threshold 를 지우는 회귀는 못 잡는다 — Apache 는 헤더 없이도 조기 응답을 읽는다.
        val missing = S3FileStorage(client, "없는-버킷-9999")

        for (size in listOf(1_000_000, 2 * 1024 * 1024)) {
            val ex = runCatching { missing.put("big.bin", ByteArray(size), "application/octet-stream") }
                .exceptionOrNull()

            assertTrue(ex is FileStorageException, "size=$size: $ex")
            assertFalse(ex.retryable, "size=$size: 재시도 대상이 아니어야 한다. 실제 원인: ${ex.cause}")
            assertContains(ex.message!!, "NoSuchBucket", message = "size=$size")
        }
    }
}
