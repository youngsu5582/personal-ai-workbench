package dev.joyson.aiworkbench.generation.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * 만들어진 파일에 대한 사실들.
 *
 * `provider` 의 같은 이름 타입을 그대로 저장하지 않는 이유: 저장되는 모양은 저장하는 모듈이 소유한다.
 * Provider 쪽 타입을 박으면 그쪽 필드명이 우리 DB 포맷이 되어, Provider 를 조정할 때 기존 행을 못 읽는다.
 *
 * 컬럼으로 쪼개지 않고 JSON 한 칸에 담는다 — 이 값으로 검색하거나 집계할 요구가 없다.
 *
 * 종류마다 아는 사실이 달라 **종류로 가른다.** 칸을 nullable 로 풀면 오디오 행에 가로·세로 칸이 남고,
 * 어느 칸이 어느 종류의 것인지를 읽는 쪽이 다시 추측해야 한다.
 * 판별자가 생기기 전의 행에는 `type` 이 없고 모두 이미지라, 판별자가 없으면 [Image] 로 읽는다.
 * 그래서 저장된 행을 고치지 않는다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = FileMetadata.Image::class)
@JsonSubTypes(
    JsonSubTypes.Type(value = FileMetadata.Image::class, name = "image"),
    JsonSubTypes.Type(value = FileMetadata.Audio::class, name = "audio"),
)
sealed interface FileMetadata {

    /** `image/png` · `audio/wav` 처럼. 내려받을 때 확장자와 Content-Type 을 정한다. */
    val mimeType: String

    val fileSize: Int

    data class Image(
        val width: Int,
        val height: Int,
        override val mimeType: String,
        override val fileSize: Int,
    ) : FileMetadata {
        init {
            require(width > 0 && height > 0) { "가로·세로는 양수여야 한다" }
            require(fileSize > 0) { "파일 크기는 양수여야 한다" }
        }
    }

    data class Audio(
        /** 재생 길이. 저쪽이 말해 준 값이 아니라 바이트에서 계산한 값이다. */
        val durationSeconds: Double,
        override val mimeType: String,
        override val fileSize: Int,
    ) : FileMetadata {
        init {
            require(durationSeconds > 0) { "재생 길이는 양수여야 한다" }
            require(fileSize > 0) { "파일 크기는 양수여야 한다" }
        }
    }
}
