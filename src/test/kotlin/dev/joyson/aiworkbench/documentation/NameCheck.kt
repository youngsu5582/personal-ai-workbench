package dev.joyson.aiworkbench.documentation

/**
 * 백틱 속 이름을 모양으로 갈라 코드와 스키마에 대조한다.
 *
 * 코드 이름 모양이 아니면(`202` · `ddl-auto: validate` · 경로) 보지 않는다. 파일 이름(`*.kt` 등)도 아직 보지 않는다.
 */
internal class NameCheck(private val code: CodeNames, private val schema: SchemaNames) {

    fun check(doc: MarkdownDocument): List<DocProblem> = doc.lines.flatMap { line ->
        line.codeSpans.mapNotNull(::problemOf).map { DocProblem(line.number, it) }
    }

    private fun problemOf(raw: String): String? {
        val name = raw.trim().removeSuffix("()")
        if (FILE.containsMatchIn(name) || name in EXTERNAL_NAMES) return null
        QUALIFIED.matchEntire(name)?.destructured?.let { (type, member) -> return checkMember(raw, type, member) }
        if (TYPE.matches(name)) return checkType(raw, name)
        TABLE_COLUMN.matchEntire(name)?.destructured?.let { (table, column) -> return checkColumn(raw, table, column) }
        if (SNAKE.matches(name)) return checkSnake(raw, name)
        return null
    }

    private fun checkType(raw: String, type: String) =
        if (code.knows(type)) null else "`$raw` — 코드에 없는 이름이다"

    /** 테스트 이름(`UserRegistryTest.마지막 로그인 수단은 해제할 수 없다`)도 멤버로 본다. 남의 클래스는 이름만 본다. */
    private fun checkMember(raw: String, type: String, member: String) = when {
        !code.isOurs(type) -> checkType(raw, type)
        code.hasMember(type, member) -> null
        else -> "`$raw` — $type 에 $member 가 없다"
    }

    /** `compose.yaml` 처럼 테이블이 아닌 것도 이 모양이라, 아는 테이블일 때만 컬럼을 본다. */
    private fun checkColumn(raw: String, table: String, column: String): String? {
        val columns = schema.columnsOf(table) ?: return null
        return if (column in columns) null else "`$raw` — $table 테이블에 $column 컬럼이 없다"
    }

    private fun checkSnake(raw: String, name: String) =
        if (schema.knows(name)) null else "`$raw` — 지금 스키마에 없는 테이블 · 컬럼이다"

    companion object {
        /**
         * 우리 코드에 없는 것이 맞는 이름. 우리가 쓰지 않는 남의 것이라 검사에서 뺀다.
         * 값은 로직이 읽지 않는다 — 왜 빠졌는지 사람이 판단하라고 붙인 이유다.
         */
        val EXTERNAL_NAMES = mapOf(
            "Authorization" to "HTTP 헤더",
            "Location" to "HTTP 헤더",
            "Blob" to "브라우저 타입",
            "MultipartFile" to "일부러 쓰지 않는 Spring 타입 — 직접 업로드를 택한 이유에 나온다",
        )

        private val QUALIFIED = Regex("([A-Z][a-z][A-Za-z0-9]*)\\.(.+)")
        private val TYPE = Regex("[A-Z][a-z][A-Za-z0-9]*")
        private val TABLE_COLUMN = Regex("([a-z][a-z0-9_]*)\\.([a-z][a-z0-9_]*)")
        private val SNAKE = Regex("[a-z][a-z0-9]*(_[a-z0-9]+)+")
        private val FILE = Regex("\\.(kt|kts|java|md|sql|ya?ml|json|sh|ts|cjs|svg|png|toml|properties|txt|html)$")
    }
}
