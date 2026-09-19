package dev.joyson.aiworkbench.generation.infrastructure

import java.util.UUID

/**
 * 보관소 키를 만든다.
 *
 * 키는 부르는 쪽이 정한다 — 경로에 작업 구조가 드러나는 것은 도메인 지식이고 보관소는 그 의미를 모른다.
 *
 * **Task 와 순번만으로 결정된다.** 재시도가 같은 키를 다시 쓰게 해서, 실패한 시도의 파일이
 * 남지 않고 덮어써지도록 한다. 시각이나 난수를 섞으면 재시도마다 잔해가 쌓인다.
 *
 * `jobs/{jobUuid}/` 로 묶여 있어 작업을 지울 때 접두사 하나로 결과를 함께 지운다.
 * 나중에 입력 파일이 생기면 `jobs/{jobUuid}/inputs/` 가 옆에 붙는다.
 */
object StorageKeys {

    fun generatedFile(jobUuid: UUID, taskUuid: UUID, sequence: Int, mimeType: String): String =
        "jobs/$jobUuid/tasks/$taskUuid/$sequence.${extensionOf(mimeType)}"

    /** `image/png` → `png`. 모르는 형식은 `bin` 으로 둔다 — 확장자가 없는 것보다 낫다. */
    private fun extensionOf(mimeType: String): String =
        mimeType.substringAfterLast('/', "").ifBlank { "bin" }
}
