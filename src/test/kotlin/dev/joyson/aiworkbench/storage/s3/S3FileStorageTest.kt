package dev.joyson.aiworkbench.storage.s3

import dev.joyson.aiworkbench.storage.FileStorageException
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import software.amazon.awssdk.services.s3.S3Client
import kotlin.test.Test
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
 * path-style 과 체크섬 설정이 검증 밖으로 빠지면 이 테스트는 초록인데 운영만 깨진다.
 *
 * ### 왜 MinIO 가 아니라 Garage 인가
 *
 * 홈서버에 올릴 것이 Garage 라서다. 둘 다 S3 를 말하지만 미묘한 차이(서명·체크섬 처리)는
 * 실제로 존재하고, 그 종류의 함정은 이미 한 번 밟았다(SDK 2.30 의 CRC32).
 * **테스트가 운영과 다른 구현을 보면 그 차이는 운영에서만 드러난다.**
 *
 * 전용 Testcontainers 모듈이 없어 [GenericContainer] 로 조립한다. 부트스트랩이 필요한 것은
 * Garage 가 분산 스토리지라서다 — 노드가 하나여도 "이 노드에 얼마를 할당한다"(layout)를
 * 선언해야 데이터를 받는다. 그 단계를 빼면 컨테이너는 떴는데 PUT 이 실패한다.
 */
class S3FileStorageTest {

    companion object {
        /** 태그를 고정한다. `latest` 는 어제 통과한 테스트가 오늘 깨지는 길이다. */
        private const val IMAGE = "dxflrs/garage:v2.4.1"
        private const val BUCKET = "workbench-test"
        private const val API_PORT = 3900

        /**
         * 자격증명을 **고정한다.** `garage key create` 는 값을 만들어 돌려주는데,
         * 그러면 테스트가 그 출력을 파싱해야 한다. `key import` 로 정해둔 값을 넣는 쪽이 단순하다.
         * Key ID 는 `GK` + hex 24자 형식을 지킨다.
         */
        private const val ACCESS_KEY = "GK00000000000000000000test"
        private const val SECRET_KEY = "0000000000000000000000000000000000000000000000000000000000000001"

        private val config = """
            metadata_dir = "/var/lib/garage/meta"
            data_dir = "/var/lib/garage/data"
            db_engine = "sqlite"
            replication_factor = 1
            rpc_bind_addr = "[::]:3901"
            rpc_public_addr = "127.0.0.1:3901"
            rpc_secret = "${"0".repeat(63)}1"

            [s3_api]
            s3_region = "garage"
            api_bind_addr = "[::]:$API_PORT"
            root_domain = ".s3.garage.localhost"
        """.trimIndent()

        /** JVM 당 한 번만 띄운다. `PostgresTestDatabase` 와 같은 이유다 — 컨테이너 기동이 테스트보다 비싸다. */
        private val garage = GenericContainer(IMAGE)
            .withExposedPorts(API_PORT)
            .withCopyToContainer(Transferable.of(config), "/etc/garage.toml")
            .waitingFor(Wait.forLogMessage(".*S3 API server listening.*", 1))
            .apply { start(); bootstrap() }

        /**
         * 셸이 없는 이미지라 명령을 엮을 수 없다. 대신 한 줄씩 부르고 노드 ID 는 여기서 판다.
         * compose 에서는 같은 일을 사람이 한 번 손으로 한다(README).
         */
        private fun GenericContainer<*>.bootstrap() {
            fun run(vararg args: String) = execInContainer(*args).also {
                check(it.exitCode == 0) { "garage ${args.joinToString(" ")} 실패: ${it.stderr}" }
            }

            // `<node id>@<host>:<port>` 로 나온다. layout 은 앞부분만 받는다.
            val nodeId = run("/garage", "node", "id", "-q").stdout.trim().substringBefore('@')

            run("/garage", "layout", "assign", "-z", "dc1", "-c", "1G", nodeId)
            run("/garage", "layout", "apply", "--version", "1")
            run("/garage", "bucket", "create", BUCKET)
            run("/garage", "key", "import", "--yes", "-n", "app-key", ACCESS_KEY, SECRET_KEY)
            run("/garage", "bucket", "allow", "--read", "--write", BUCKET, "--key", "app-key")
        }

        private fun properties(bucket: String? = BUCKET) = S3StorageProperties(
            bucket = bucket,
            region = "garage",
            endpoint = "http://${garage.host}:${garage.getMappedPort(API_PORT)}",
            accessKey = ACCESS_KEY,
            secretKey = SECRET_KEY,
            // 로컬 컨테이너에는 `bucket.host` 를 풀어줄 DNS 가 없다.
            pathStyleAccess = true,
        )

        private val client: S3Client = S3Clients.of(properties())
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
        assertFalse(ex.retryable)
    }
}
