package dev.joyson.aiworkbench.storage.s3

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * 발급한 **업로드** 주소가 실제로 통하는지 확인한다.
 *
 * [S3PresignedUrlIssuerTest] 와 같은 이유로 진짜 보관소에 쏜다 — 서명은 로컬 계산이라
 * 주소 모양만 보면 초록이 쉽게 나오는데, 이 기능의 실패는 대부분 **보관소가 그 서명을 거부하는 것**이다.
 *
 * 여기서 특히 보려는 것은 `내용 형식을 서명이 묶어 두는가` 다. 이 경로에서는 앱이 바이트를
 * 보지 못하므로, 그 단언이 성립해야 "아무거나 올릴 수는 없다" 고 말할 수 있다.
 */
class S3PresignedUploadIssuerTest {

    companion object {
        private val BUCKET = GarageTestStorage.BUCKET
        private val properties = GarageTestStorage.properties()
        private val storage = S3FileStorage(S3Clients.of(properties), BUCKET)
    }

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    private fun issuer(ttl: Duration = Duration.ofMinutes(5)) = S3PresignedUploadIssuer(
        presigner = S3Clients.presigner(properties),
        bucket = BUCKET,
        ttl = ttl,
    )

    private fun put(url: URI, body: ByteArray, contentType: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(url)
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `발급한 주소로 올린 바이트를 보관소가 그대로 갖는다`() {
        val key = "uploads/a/aaaa.png"

        val response = put(issuer().issueUpload(key, "image/png", png.size.toLong()), png, "image/png")

        assertEquals(200, response.statusCode())
        // 앱을 통과하지 않고 들어온 바이트다.
        assertContentEquals(png, storage.read(key))
    }

    /**
     * 이 단언이 이 경로의 **유일한 형식 통제**다.
     *
     * 서버가 바이트를 보지 못하므로, 서명이 형식을 묶어두지 못하면 발급받은 사람이
     * 무엇이든 올릴 수 있게 된다. 깨지면 형식 검사를 다른 자리에서 다시 만들어야 한다.
     */
    @Test
    fun `서명한 형식과 다르게 올리면 거부된다`() {
        val key = "uploads/a/bbbb.png"
        val url = issuer().issueUpload(key, "image/png", png.size.toLong())

        val response = put(url, png, "text/plain")

        // 상태 코드를 못 박지 않는다 — 구현마다 403·400 이 갈린다. 지키려는 것은 "못 올린다" 다.
        assertNotEquals(200, response.statusCode())
        assertNull(storage.read(key), "거부됐는데 보관소에 남았다")
    }

    @Test
    fun `서명한 크기와 다르게 올리면 거부된다`() {
        val key = "uploads/a/wrong-length.png"
        val url = issuer().issueUpload(key, "image/png", png.size.toLong())

        assertNotEquals(200, put(url, png + byteArrayOf(1), "image/png").statusCode())
        assertNull(storage.read(key), "거부됐는데 보관소에 남았다")
    }

    @Test
    fun `서명이 없으면 보관소가 거부한다`() {
        // 이 전제가 깨지면 presigned 를 쓸 이유가 없다 — 주소만 알면 누구나 쓰게 된다.
        val key = "uploads/a/cccc.png"

        val naked = put(URI.create("${properties.endpoint}/$BUCKET/$key"), png, "image/png")

        assertNotEquals(200, naked.statusCode())
        assertNull(storage.read(key))
    }

    @Test
    fun `수명이 지난 주소로는 올릴 수 없다`() {
        // 쓰기 주소는 유출되면 남이 우리 보관소에 쓴다. 수명이 곧 그 창이다.
        val key = "uploads/a/dddd.png"
        val url = issuer(ttl = Duration.ofSeconds(1)).issueUpload(key, "image/png", png.size.toLong())

        Thread.sleep(1_500)

        assertNotEquals(200, put(url, png, "image/png").statusCode())
        assertNull(storage.read(key))
    }
}
