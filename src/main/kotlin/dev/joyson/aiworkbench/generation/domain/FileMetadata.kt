package dev.joyson.aiworkbench.generation.domain

/**
 * 만들어진 파일에 대한 사실들.
 *
 * `provider` 의 같은 이름 타입을 그대로 저장하지 않는 이유: 저장되는 모양은 저장하는 모듈이 소유한다.
 * Provider 쪽 타입을 박으면 그쪽 필드명이 우리 DB 포맷이 되어, Provider 를 조정할 때 기존 행을 못 읽는다.
 *
 * 컬럼으로 쪼개지 않고 JSON 한 칸에 담는다 — 이 값으로 검색하거나 집계할 요구가 없다.
 * "이미지" 가 아니라 "파일" 인 것은 나중에 영상도 같은 자리에 담기 때문이다.
 */
data class FileMetadata(
    val width: Int,
    val height: Int,
    val mimeType: String,
    val fileSize: Int,
) {
    init {
        require(width > 0 && height > 0) { "가로·세로는 양수여야 한다" }
        require(fileSize > 0) { "파일 크기는 양수여야 한다" }
    }
}
