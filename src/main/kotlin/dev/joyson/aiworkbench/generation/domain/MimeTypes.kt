package dev.joyson.aiworkbench.generation.domain

/**
 * 형식에서 확장자를 얻는다.
 *
 * 내려받을 때 파일명에 붙일 확장자를 정한다.
 */
object MimeTypes {

    /** `image/png` → `png`. 모르는 형식은 `bin` 으로 둔다 — 확장자가 없는 것보다 낫다. */
    fun extensionOf(mimeType: String): String =
        mimeType.substringAfterLast('/', "").ifBlank { "bin" }
}
