package dev.joyson.aiworkbench.generation.domain

import com.fasterxml.jackson.annotation.JsonValue

/**
 * 결과 파일을 모아 볼 때 가르는 종류.
 *
 * [FileMetadata] 의 판별자가 아니라 **형식의 앞부분**으로 가른다. 판별자가 생기기 전의 행에는 `type` 이 없어서,
 * 판별자로 거르면 그 행들이 어느 종류에도 들지 않는다.
 */
enum class FileKind(@get:JsonValue val value: String, val mimeTypePrefix: String) {
    IMAGE("image", "image/"),

    AUDIO("audio", "audio/"),
}
