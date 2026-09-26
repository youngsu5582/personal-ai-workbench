package dev.joyson.aiworkbench.storage.s3

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable

/**
 * 테스트용 Garage 한 대.
 *
 * **JVM 당 하나만 띄운다.** `PostgresTestDatabase` 와 같은 이유다 — 컨테이너 기동이 테스트보다 비싸고,
 * 이 클래스를 쓰는 테스트가 둘 이상이라 각자 띄우면 그만큼 곱해진다.
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
object GarageTestStorage {

    /** 태그를 고정한다. `latest` 는 어제 통과한 테스트가 오늘 깨지는 길이다. */
    private const val IMAGE = "dxflrs/garage:v2.4.1"
    private const val API_PORT = 3900

    const val BUCKET = "workbench-test"

    /**
     * 자격증명을 **고정한다.** `garage key create` 는 값을 만들어 돌려주므로 출력을 파싱해야 한다.
     * `key import` 로 정해둔 값을 넣는 쪽이 단순하다. Key ID 는 `GK` + hex 24자 형식을 지킨다.
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

    private val container = GenericContainer(IMAGE)
        .withExposedPorts(API_PORT)
        .withCopyToContainer(Transferable.of(config), "/etc/garage.toml")
        .waitingFor(Wait.forLogMessage(".*S3 API server listening.*", 1))
        .apply { start(); bootstrap() }

    /**
     * 셸이 없는 이미지라 명령을 엮을 수 없다. 한 줄씩 부르고 노드 ID 는 여기서 판다.
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

    /**
     * 실제로 **쓰고 읽을 수 있을 때까지** 기다린다.
     *
     * 로그("S3 API server listening")는 포트가 열린 것만 말한다. layout 을 적용한 직후에는
     * 노드가 아직 데이터를 받지 못해 5xx 가 나오는 순간이 있고, 그러면 SDK 가 재시도하다 포기해
     * **연결 계열 실패**로 분류된다 — 테스트가 기대한 404 와 다른 결과가 나온다.
     *
     * CI 에서 `없는 버킷은 다시 해도 소용없다` 가 한 번 이렇게 깨졌다. 로컬에서는 컨테이너가 빨라
     * 재현되지 않았다. 한 테스트만 고치지 않는 이유는, 준비 안 된 노드는 **어느 테스트든** 흔들 수 있어서다.
     */
    private fun awaitWritable() {
        val storage = S3FileStorage(S3Clients.of(properties()), BUCKET)
        val probe = byteArrayOf(1)

        repeat(ATTEMPTS) { attempt ->
            val ready = runCatching {
                storage.put(READY_KEY, probe, "application/octet-stream")
                storage.read(READY_KEY) != null
            }.getOrDefault(false)

            if (ready) {
                // 한 번에 되면 조용하다. 두 번 이상 걸렸다는 것은 **그 창이 실재한다**는 증거이고,
                // 로컬에서는 재현되지 않아 CI 로그로만 확인할 수 있다.
                if (attempt > 0) println("Garage 가 쓰기 가능해지기까지 ${attempt + 1}번 걸렸다")
                return
            }
            check(attempt < ATTEMPTS - 1) { "Garage 가 ${ATTEMPTS}번 안에 쓰기 가능해지지 않았다" }
            Thread.sleep(INTERVAL_MS)
        }
    }

    private const val ATTEMPTS = 30
    private const val INTERVAL_MS = 300L
    private const val READY_KEY = "_ready"

    fun properties(bucket: String? = BUCKET) = S3StorageProperties(
        bucket = bucket,
        region = "garage",
        endpoint = "http://${container.host}:${container.getMappedPort(API_PORT)}",
        accessKey = ACCESS_KEY,
        secretKey = SECRET_KEY,
        // 로컬 컨테이너에는 `bucket.host` 를 풀어줄 DNS 가 없다.
        pathStyleAccess = true,
    )

    init {
        awaitWritable()
    }
}
