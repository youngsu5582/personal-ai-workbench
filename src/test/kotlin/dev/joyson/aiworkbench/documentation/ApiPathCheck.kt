package dev.joyson.aiworkbench.documentation

/**
 * 백틱 속 `/api/…` 경로가 실제로 열려 있는지 본다.
 *
 * `/api/` 로 시작하는 것만 본다 — 우리가 정한 이름공간이라 OpenAI 나 Spring Security 의 경로가 섞이지 않는다.
 * 앞에 붙은 HTTP 메서드(`GET /api/me`)와 뒤의 쿼리(`?from=…`)는 떼고 경로만 맞춘다. `*` 가 든 패턴은 경로가 아니라 보지 않는다.
 */
internal class ApiPathCheck(private val api: ApiPaths) {

    fun check(doc: MarkdownDocument): List<DocProblem> = doc.lines.flatMap { line ->
        line.codeSpans
            .mapNotNull(::pathIn)
            .filterNot(api::opens)
            .map { DocProblem(line.number, "`$it` — 그런 API 경로가 없다") }
    }

    private fun pathIn(span: String): String? =
        API_PATH.find(span)?.groupValues?.get(1)?.substringBefore('?')?.takeUnless { '*' in it }

    private companion object {
        val API_PATH = Regex("(?:^|\\s)(/api(?:/\\S*)?)")
    }
}
