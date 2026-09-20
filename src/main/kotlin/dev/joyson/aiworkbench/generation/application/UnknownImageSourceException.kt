package dev.joyson.aiworkbench.generation.application

import java.util.UUID

/**
 * 참조한 입력 이미지를 쓸 수 없다.
 *
 * **없는 것인지 남의 것인지 구분해 말하지 않는다.** 구분하면 "그 uuid 는 존재한다" 가
 * 새어 나간다 — 남의 Job 조회에 404 를 주는 것과 같은 규칙이다.
 *
 * 상태 코드를 여기서 정하지 않는 이유는 [UnsupportedModelException] 과 같다.
 */
class UnknownImageSourceException(
    val sourceUuid: UUID,
) : RuntimeException("참조한 입력 이미지를 쓸 수 없다: $sourceUuid")
