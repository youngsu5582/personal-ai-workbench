package dev.joyson.aiworkbench.documentation

import com.tngtech.archunit.core.importer.ClassFileImporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import tools.jackson.databind.json.JsonMapper
import java.io.File

/**
 * 문서가 가리키는 이름과 링크가 지금 코드에 있는지 본다.
 *
 * 문서는 문서를 고칠 때가 아니라 **코드를 고칠 때** 낡는다. 컬럼 이름을 바꾸는 마이그레이션은 문서를
 * 건드리지 않는다. 그래서 문서를 쓰는 순간이 아니라 코드가 바뀔 때마다 도는 이 테스트가 맡는다.
 *
 * 잡는 것은 가리키는 이름이 사라진 것과 링크가 깨진 것뿐이다. 이름은 그대로인데 동작이 바뀐 것,
 * 이름이 아닌 사실 문장은 잡지 못한다. 그런 문장은 문서에 쓰지 않는 것이 먼저다.
 */
class DocumentationTest {

    @TestFactory
    fun `문서가 가리키는 이름과 링크가 지금 있다`(): List<DynamicTest> = DOCS.map { file ->
        dynamicTest(file.path) {
            val problems = check(MarkdownDocument.of(file))
            assertTrue(problems.isEmpty()) {
                buildString {
                    appendLine("${file.path} 에서 ${problems.size}건")
                    problems.forEach { appendLine("  ${file.path}:${it.line}  ${it.message}") }
                    appendLine("옛 이름을 설명하는 문장이라면 지운다 — 그 이력은 커밋 메시지가 갖는다.")
                    append("우리가 쓰지 않는 남의 이름이라면 NameCheck.EXTERNAL_NAMES 에 이유와 함께 더한다.")
                }
            }
        }
    }

    // 아래 셋은 검사가 살아 있는지 본다. 이름을 잘못 읽으면 아무것도 걸리지 않아 모든 문서가 **조용히 통과한다.**
    // 검사 종류마다 틀린 줄과 맞는 줄을 두고, 틀린 줄만 정확히 잡히는지 본다.

    @Test
    fun `이름 검사는 없는 이름만 잡는다`() = assertCaught(
        "`UserWriter` 와 `UserWriter.unlink` 는 있다" to false,
        "`UserRegistryTest.마지막 로그인 수단은 해제할 수 없다` 도 있다" to false,
        "`owner_user_uuid` 와 `users.uuid` 도 있다" to false,
        "우리 코드가 쓰는 `Long` 과 남의 이름 `Authorization` 도 된다" to false,
        "`NoSuchThing` 은 없다" to true,
        "`UserWriter.noSuchMember` 는 없다" to true,
        "`UserRegistryTest.없는 테스트 이름` 은 없다" to true,
        "`no_such_column` 은 없다" to true,
        "`users.no_such_column` 은 없다" to true,
    )

    @Test
    fun `링크 검사는 깨진 링크와 줄 번호 링크만 잡는다`() = assertCaught(
        "# 있는 제목" to false,
        "[README](README.md) 와 [제목](#있는-제목) 과 [바깥](https://example.com) 은 된다" to false,
        "[SHA 로 고정한 줄](https://github.com/o/r/blob/3fc5697/README.md#L3) 도 된다" to false,
        "[없는 파일](no-such-file.md)" to true,
        "[없는 제목](#없는-제목)" to true,
        "[다른 문서의 없는 제목](README.md#없는-제목)" to true,
        "[줄 번호](README.md#L3)" to true,
        "[브랜치의 줄](https://github.com/o/r/blob/main/README.md#L3)" to true,
    )

    @Test
    fun `API 경로 검사는 열려 있지 않은 경로만 잡는다`() = assertCaught(
        "`POST /api/jobs` 와 `GET /api/jobs/{uuid}` 는 열려 있다" to false,
        "변수 자리를 다르게 적은 `/api/jobs/123` 과 쿼리를 붙인 `GET /api/usage?from=2026-10-01` 도 된다" to false,
        "우리 것이 아닌 `/v1/images/edits` 와 패턴 `/api/**` 은 보지 않는다" to false,
        "`/api/no-such-path` 는 없다" to true,
        "`GET /api/jobs/{uuid}/no-such` 도 없다" to true,
    )

    @Test
    fun `코드 블록 안은 보지 않는다`() = assertCaught(
        "```" to false,
        "`NoSuchThing` 은 코드 블록 안이다" to false,
        "```" to false,
        "`NoSuchThing` 은 코드 블록 밖이다" to true,
    )

    /** 각 줄이 (내용, 잡혀야 하는가). 저장소 루트를 문서 폴더로 삼아 상대 링크를 푼다. */
    private fun assertCaught(vararg fixture: Pair<String, Boolean>) {
        val doc = MarkdownDocument(fixture.joinToString("\n") { it.first }, File("."))
        val problems = check(doc)
        val expected = fixture.withIndex().filter { it.value.second }.map { it.index + 1 }.toSet()
        assertEquals(expected, problems.map { it.line }.toSet()) { problems.joinToString("\n") }
    }

    private companion object {
        /** 검사할 문서. 파일이면 그 파일, 폴더면 그 아래 모든 `.md`. 넓힐 때는 여기에 하나를 더한다. */
        val DOC_PATHS = listOf("README.md", "CLAUDE.md", "AGENTS.md", "docs")

        val DOCS: List<File> = DOC_PATHS.map(::File).flatMap { path ->
            if (path.isDirectory) path.walk().filter { it.isFile && it.extension == "md" }.toList() else listOf(path)
        }.filter { it.exists() }.sortedBy { it.path }

        @Suppress("UNCHECKED_CAST")
        val BASE_PACKAGE = (JsonMapper.builder().build()
            .readValue(File(".claude/conventions.json"), Map::class.java) as Map<String, Any?>)["basePackage"] as String

        /** 테스트 클래스도 함께 읽는다 — 문서가 테스트 이름을 가리킬 수 있다. 테스트 쪽에는 컨트롤러가 없다. */
        val CLASSES = ClassFileImporter().importPackages(BASE_PACKAGE)

        val NAMES = NameCheck(CodeNames(CLASSES), SchemaNames.current())
        val API = ApiPathCheck(ApiPaths.of(CLASSES))

        fun check(doc: MarkdownDocument) = NAMES.check(doc) + LinkCheck.check(doc) + API.check(doc)
    }
}
