package dev.joyson.aiworkbench.generation.domain

/**
 * 형식에서 확장자를 얻는다.
 *
 * 보관소 키와 내려받을 때의 파일명이 같은 규칙을 쓰도록 한 곳에 둔다.
 * 키가 내용 기반으로 바뀌면 키 쪽은 확장자를 버리지만, 파일명은 계속 필요하다.
 */
object MimeTypes {

    /** `image/png` → `png`. 모르는 형식은 `bin` 으로 둔다 — 확장자가 없는 것보다 낫다. */
    fun extensionOf(mimeType: String): String =
        mimeType.substringAfterLast('/', "").ifBlank { "bin" }
}
