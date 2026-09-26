package dev.joyson.aiworkbench.storage.config

import dev.joyson.aiworkbench.storage.local.LocalStorageProperties
import dev.joyson.aiworkbench.storage.s3.S3StorageProperties
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 보관소가 기동에 남기는 한 줄을 확인한다.
 *
 * 이 줄의 목적은 **"지금 이 앱이 어디에 쓰고 있나" 를 로그만 보고 답하는 것**이다.
 * 그래서 확인할 것이 둘이다 — 가릴 수 있을 만큼 담겼는가, 담지 말아야 할 것이 안 담겼는가.
 *
 * 설명은 **팩토리가** 만든다. 그래야 보관소를 더할 때(R2 등) 고칠 자리가 그 팩토리 하나다.
 */
class StorageSummaryTest {

    private val config = StorageConfig()

    private val s3 = S3StorageProperties(
        bucket = "workbench",
        region = "garage",
        endpoint = "http://storage.example.com:3900",
        accessKey = "GKsecretlookingkey",
        secretKey = "supersecret",
        pathStyleAccess = true,
    )

    @Test
    fun `로컬은 실제 경로를 남긴다`() {
        val detail = config.localFileStorageFactory(LocalStorageProperties(root = "./var/assets")).describe()

        assertContains(detail, "/var/assets")
        // 상대 경로는 작업 디렉토리를 모르면 아무것도 말해주지 않는다.
        assertFalse(detail.contains("./var/assets"), "절대 경로여야 한다: $detail")
    }

    @Test
    fun `s3 는 어디에 쓰는지를 가릴 수 있게 남긴다`() {
        val detail = config.s3FileStorageFactory(s3).describe()

        assertContains(detail, "endpoint=http://storage.example.com:3900")
        assertContains(detail, "bucket=workbench")
        assertContains(detail, "region=garage")
        assertContains(detail, "pathStyle=true")
    }

    @Test
    fun `자격증명은 값이 아니라 여부만 남긴다`() {
        val detail = config.s3FileStorageFactory(s3).describe()

        // 로그는 남의 눈에 띄기 쉬운 자리다. 키가 여기 실리면 저장소·수집기로 흘러간다.
        assertFalse(detail.contains("supersecret"), "비밀키가 새어 나갔다: $detail")
        assertFalse(detail.contains("GKsecretlookingkey"), "접근키가 새어 나갔다: $detail")
        assertContains(detail, "자격증명=직접 지정")
    }

    @Test
    fun `자격증명을 안 주면 SDK 기본 탐색이라고 남긴다`() {
        // 배포에서 역할 기반 인증을 쓰면 이쪽이 정상이다. "키가 빠졌다" 와 구분되어야 한다.
        val detail = config.s3FileStorageFactory(s3.copy(accessKey = null, secretKey = null)).describe()

        assertContains(detail, "자격증명=SDK 기본 탐색")
    }

    @Test
    fun `서명할 수 있는지가 드러난다`() {
        // previewUrl 이 안 나오는 이유를 여기서 바로 알 수 있어야 한다.
        val ttl = Duration.ofMinutes(15)

        assertContains(describeStorage("s3", "detail", presigning = true, ttl), "서명주소=발급함(15분)")
        assertTrue(describeStorage("local", "detail", presigning = false, ttl).contains("발급 못 함"))
    }
}
