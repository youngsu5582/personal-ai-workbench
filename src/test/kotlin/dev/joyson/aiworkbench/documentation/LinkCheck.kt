package dev.joyson.aiworkbench.documentation

import java.io.File

/**
 * 상대 링크가 가리키는 파일과 제목이 있는지 본다. 바깥 주소는 열어 보지 않는다.
 *
 * 줄 번호로 가리키는 링크는 걸린다 — 줄은 코드가 바뀌면 조용히 어긋나고 링크는 여전히 열린다.
 * 바깥 주소도 플랫폼을 따지지 않고 같다. 경로에 커밋 SHA 가 있으면 과거를 가리키므로 통과한다.
 */
internal object LinkCheck {

    fun check(doc: MarkdownDocument): List<DocProblem> = doc.lines.flatMap { line ->
        line.links.mapNotNull { problemOf(it, doc) }.map { DocProblem(line.number, it) }
    }

    private fun problemOf(target: String, doc: MarkdownDocument): String? {
        if (EXTERNAL.containsMatchIn(target)) return checkExternalLine(target)
        val path = target.substringBefore('#')
        val anchor = target.substringAfter('#', "")
        return when {
            LINE_ANCHOR.matches(anchor) -> "($target) — 줄 번호로 가리킨다. 줄은 코드가 바뀌면 조용히 어긋난다"
            path.isEmpty() -> if (anchor in doc.anchors) null else "($target) — 이 문서에 그런 제목이 없다"
            else -> checkFile(target, File(doc.dir, path), anchor)
        }
    }

    private fun checkExternalLine(target: String): String? {
        if (!LINE_ANCHOR.matches(target.substringAfter('#', ""))) return null
        val pinned = target.substringBefore('#').split('/').any { SHA.matches(it) }
        return if (pinned) null else "($target) — 커밋으로 고정하지 않은 줄 번호로 가리킨다. 줄은 코드가 바뀌면 조용히 어긋난다"
    }

    private fun checkFile(target: String, file: File, anchor: String) = when {
        !file.exists() -> "($target) — 그런 파일이 없다"
        anchor.isEmpty() || file.extension != "md" -> null
        anchor in anchorsOf(file) -> null
        else -> "($target) — ${file.name} 에 그런 제목이 없다"
    }

    /** 여러 문서가 같은 문서의 제목을 가리켜도 한 번만 읽는다. */
    private val anchors = mutableMapOf<File, Set<String>>()

    private fun anchorsOf(file: File) = anchors.getOrPut(file.canonicalFile) { MarkdownDocument.of(file).anchors }

    private val EXTERNAL = Regex("^(https?:|mailto:)")
    private val LINE_ANCHOR = Regex("L\\d+(-L\\d+)?")
    private val SHA = Regex("[0-9a-f]{7,40}")
}
