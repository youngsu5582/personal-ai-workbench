package dev.joyson.aiworkbench.generation.application

import dev.joyson.aiworkbench.generation.domain.FileMetadata
import dev.joyson.aiworkbench.generation.domain.GeneratedFile
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 목록에 실리는 미리보기 주소를 확인한다.
 *
 * 매핑이 **순수 함수**라 스프링도 컨테이너도 필요 없다 — 발급자를 인자로 받기 때문이다.
 * 그 성질이 이 테스트로 증명된다.
 */
class GenerationJobViewsTest {

    private val fileUuid = UUID.randomUUID()

    private val file = GeneratedFile(
        uuid = fileUuid,
        taskId = 1L,
        storageKey = "users/o/blobs/ab/cd/abcd.png",
        metadata = FileMetadata(width = 1024, height = 1024, mimeType = "image/png", fileSize = 100),
    )

    @Test
    fun `발급자가 있으면 미리보기 주소가 실린다`() {
        val view = file.toView { key, fileName -> URI.create("https://storage.test/$key?name=$fileName") }

        assertEquals("https://storage.test/users/o/blobs/ab/cd/abcd.png?name=$fileUuid.png", view.previewUrl)
    }

    @Test
    fun `발급자가 없으면 null 이고 받는 쪽은 우리 경로로 폴백한다`() {
        assertNull(file.toView(presignedUrlIssuer = null).previewUrl)
    }

    @Test
    fun `내려받을 이름은 보관소 키가 아니라 uuid 로 짓는다`() {
        // 키는 내용 주소(해시)라 그대로 쓰면 64자 hex 가 파일명이 된다.
        var seenName: String? = null
        file.toView { _, fileName -> seenName = fileName; URI.create("https://x/") }

        assertEquals("$fileUuid.png", seenName)
    }
}
