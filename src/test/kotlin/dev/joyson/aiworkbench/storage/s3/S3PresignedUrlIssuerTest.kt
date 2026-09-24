package dev.joyson.aiworkbench.storage.s3

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 발급한 주소가 **실제로 통하는지** 확인한다.
 *
 * 서명은 로컬 계산이라 단위 테스트로 "주소 모양" 만 보면 초록이 쉽게 나온다. 그런데 이 기능의 실패는
 * 대부분 **보관소가 그 서명을 거부하는 것**이고, 그건 주소를 눌러봐야 안다 —
 * 그래서 발급한 주소로 진짜 HTTP 요청을 보낸다.
 */
class S3PresignedUrlIssuerTest {

    companion object {
        private val BUCKET = GarageTestStorage.BUCKET
        private val properties = GarageTestStorage.properties()
        private val storage = S3FileStorage(S3Clients.of(properties), BUCKET)
    }

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    private fun issuer(ttl: Duration = Duration.ofMinutes(5)) = S3PresignedUrlIssuer(
        presigner = S3Clients.presigner(properties),
        bucket = BUCKET,
        ttl = ttl,
    )

    private fun get(url: URI): HttpResponse<ByteArray> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(url).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )

    @Test
    fun `발급한 주소로 바이트를 그대로 받는다`() {
        val key = "users/a/blobs/11/22/1122.png"
        storage.put(key, png, "image/png")

        val response = get(issuer().issue(key, "cat.png"))

        assertEquals(200, response.statusCode())
        assertContentEquals(png, response.body())
    }

    @Test
    fun `저장할 때 실은 형식이 응답에 그대로 나온다`() {
        // 이게 있어야 브라우저가 <img> 로 그린다. 없으면 받아서 저장만 된다.
        val key = "users/a/blobs/33/44/3344.png"
        storage.put(key, png, "image/png")

        val response = get(issuer().issue(key, "cat.png"))

        assertEquals("image/png", response.headers().firstValue("content-type").orElse(null))
    }

    @Test
    fun `받는 쪽이 저장할 이름을 우리가 정한다`() {
        // 키는 내용 주소라 그대로 두면 64자 해시가 파일명이 된다.
        val key = "users/a/blobs/55/66/5566.png"
        storage.put(key, png, "image/png")

        val response = get(issuer().issue(key, "고양이.png"))

        val disposition = response.headers().firstValue("content-disposition").orElse("")
        // inline 이라 브라우저가 바로 그린다.
        assertContains(disposition, "inline")
        // HTTP 헤더는 latin-1 이라 UTF-8 이름은 RFC 5987 로 따로 실린다.
        assertContains(disposition, "filename*=UTF-8''%EA%B3%A0%EC%96%91%EC%9D%B4.png")
    }

    @Test
    fun `서명이 없으면 보관소가 거부한다`() {
        // 이 전제가 깨지면 presigned 를 쓰는 의미가 없다 — 주소만 알면 누구나 받게 된다.
        val key = "users/a/blobs/77/88/7788.png"
        storage.put(key, png, "image/png")

        val naked = get(URI.create("${properties.endpoint}/$BUCKET/$key"))

        assertEquals(403, naked.statusCode())
    }

    @Test
    fun `수명이 지난 주소는 거부된다`() {
        // 서명이 인증을 대신하므로, 수명이 곧 유출 시 피해 범위다.
        val key = "users/a/blobs/99/aa/99aa.png"
        storage.put(key, png, "image/png")

        // 1초로 발급하고 지나기를 기다린다. 이 테스트만 느린 대신, 만료가 실제로 동작함을 본다.
        val url = issuer(ttl = Duration.ofSeconds(1)).issue(key, "cat.png")
        Thread.sleep(1_500)

        // 상태 코드를 못 박지 않는다 — Garage 는 400, AWS S3 는 403 을 준다.
        // 우리가 지키려는 것은 "받을 수 없다" 이지 특정 숫자가 아니다.
        assertNotEquals(200, get(url).statusCode())
    }
}
