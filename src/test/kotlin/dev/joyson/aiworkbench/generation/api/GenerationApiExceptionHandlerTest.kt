package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.storage.FileStorageException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GenerationApiExceptionHandlerTest {

    @Test
    fun `접수 중 보관소에 닿지 못하면 503 이고 내부 사유는 응답에 싣지 않는다`() {
        val e = FileStorageException(retryable = true, message = "객체 앞부분을 읽지 못했다: users/a/uploads/b (InternalError 500)")

        val problem = GenerationApiExceptionHandler().handleStorageUnavailable(e)

        assertEquals(503, problem.status)
        assertFalse(problem.detail.orEmpty().contains("users/a"))
    }
}
