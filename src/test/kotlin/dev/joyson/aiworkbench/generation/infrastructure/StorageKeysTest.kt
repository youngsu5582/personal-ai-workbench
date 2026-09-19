package dev.joyson.aiworkbench.generation.infrastructure

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StorageKeysTest {

    private val job = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val task = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val file = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @Test
    fun `작업 구조가 드러나는 경로를 만든다`() {
        assertEquals(
            "jobs/$job/tasks/$task/$file.png",
            StorageKeys.generatedFile(job, task, file, "image/png"),
        )
    }

    @Test
    fun `같은 Task 가 낸 파일들은 서로 다른 키를 갖는다`() {
        // 원본과 썸네일이든 한 호출이 돌려준 여러 장이든, 겹쳐서 덮어쓰는 일이 없어야 한다.
        assertNotEquals(
            StorageKeys.generatedFile(job, task, UUID.randomUUID(), "image/png"),
            StorageKeys.generatedFile(job, task, UUID.randomUUID(), "image/png"),
        )
    }

    @Test
    fun `형식에서 확장자를 얻고 모르면 bin 으로 둔다`() {
        assertEquals("jobs/$job/tasks/$task/$file.jpeg", StorageKeys.generatedFile(job, task, file, "image/jpeg"))
        assertEquals("jobs/$job/tasks/$task/$file.bin", StorageKeys.generatedFile(job, task, file, "이상한형식"))
    }
}
