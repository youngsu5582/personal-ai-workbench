package dev.joyson.aiworkbench.documentation

import java.io.File

/**
 * 검사할 줄만 남긴 마크다운. 코드 블록 안은 예시라 보지 않는다.
 *
 * 일부러 건너뛰는 표시는 두지 않는다. 옛 이름을 적은 문장은 표시할 것이 아니라 지울 것이다 — 그 이력은 커밋 메시지가 갖는다.
 */
internal class MarkdownDocument(text: String, val dir: File) {

    data class Line(val number: Int, val text: String) {
        val codeSpans: List<String> get() = CODE_SPAN.findAll(text).map { it.groupValues[1] }.toList()
        val links: List<String> get() = LINKS.flatMap { r -> r.findAll(text).map { it.groupValues[1] } }
    }

    val lines: List<Line> = outsideFences(text.lines())

    val anchors: Set<String> = lines
        .mapNotNull { HEADING.matchEntire(it.text)?.groupValues?.get(1) }
        .map(::anchorOf)
        .toSet()

    companion object {
        fun of(file: File) = MarkdownDocument(file.readText(), file.parentFile ?: File("."))

        /** GitHub 가 제목으로 만드는 앵커. 소문자로 바꾸고, 글자 · 숫자 · 공백 · `-` · `_` 만 남기고, 공백을 `-` 로. */
        fun anchorOf(heading: String): String =
            heading.trim().lowercase().replace(NOT_ANCHOR, "").replace(' ', '-')

        private fun outsideFences(lines: List<String>): List<Line> {
            var fenced = false
            return lines.mapIndexedNotNull { i, text ->
                when {
                    FENCE.containsMatchIn(text) -> null.also { fenced = !fenced }
                    fenced -> null
                    else -> Line(i + 1, text)
                }
            }
        }

        private val FENCE = Regex("^\\s*(```|~~~)")
        private val HEADING = Regex("^#{1,6}\\s+(.*)$")
        private val NOT_ANCHOR = Regex("[^\\p{L}\\p{N}\\p{M} _-]")
        private val CODE_SPAN = Regex("`([^`]+)`")
        private val LINKS = listOf(Regex("\\]\\(([^)\\s]+)\\)"), Regex("(?:src|srcset)=\"([^\"]+)\""))
    }
}
