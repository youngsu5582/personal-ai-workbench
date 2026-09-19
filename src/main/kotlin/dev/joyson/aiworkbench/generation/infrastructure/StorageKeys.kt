package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.MimeTypes
import java.util.UUID

/**
 * 보관소 키를 만든다.
 *
 * 키는 부르는 쪽이 정한다 — 경로에 작업 구조가 드러나는 것은 도메인 지식이고 보관소는 그 의미를 모른다.
 *
 * **파일마다 자기 uuid 로 갈린다.** 한 Task 가 파일을 몇 개 내든 키가 겹치지 않는다 —
 * 원본과 썸네일이든, 한 호출이 돌려준 여러 장이든 마찬가지다.
 *
 * 재시도가 같은 키에 덮어쓰지는 않는다. 그러려면 키가 "Task 안에서 몇 번째" 를 담아야 하는데,
 * 그 값이 사주는 것에 비해 비싸다 — 재시도 대부분(429·5xx·타임아웃)은 바이트를 쓰기 전에 나고,
 * 남는 잔해는 put 과 기록 사이가 끊긴 드문 경우의 파일 하나다.
 * 그마저 `jobs/{jobUuid}/` 아래 묶여 있어 작업을 지울 때 함께 지워진다.
 *
 * `jobs/{jobUuid}/` 로 묶여 있어 작업을 지울 때 접두사 하나로 결과를 함께 지운다.
 * 나중에 입력 파일이 생기면 `jobs/{jobUuid}/inputs/` 가 옆에 붙는다.
 */
object StorageKeys {

    fun generatedFile(jobUuid: UUID, taskUuid: UUID, fileUuid: UUID, mimeType: String): String =
        "jobs/$jobUuid/tasks/$taskUuid/$fileUuid.${MimeTypes.extensionOf(mimeType)}"
}
