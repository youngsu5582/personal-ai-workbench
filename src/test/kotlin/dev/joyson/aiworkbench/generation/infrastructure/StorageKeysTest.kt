package dev.joyson.aiworkbench.generation.infrastructure

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class StorageKeysTest {

    private val job = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val task = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `작업 구조가 드러나는 경로를 만든다`() {
        assertEquals(
            "jobs/$job/tasks/$task/0.png",
            StorageKeys.generatedFile(job, task, 0, "image/png"),
        )
    }

    @Test
    fun `같은 Task 와 순번이면 항상 같은 키다`() {
        // 재시도가 같은 키에 덮어써야 실패한 시도의 파일이 남지 않는다.
        assertEquals(
            StorageKeys.generatedFile(job, task, 1, "image/png"),
            StorageKeys.generatedFile(job, task, 1, "image/png"),
        )
    }

    @Test
    fun `형식에서 확장자를 얻고 모르면 bin 으로 둔다`() {
        assertEquals("jobs/$job/tasks/$task/0.jpeg", StorageKeys.generatedFile(job, task, 0, "image/jpeg"))
        assertEquals("jobs/$job/tasks/$task/0.bin", StorageKeys.generatedFile(job, task, 0, "이상한형식"))
    }
}
