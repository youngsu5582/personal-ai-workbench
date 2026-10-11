package dev.joyson.aiworkbench

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import tools.jackson.databind.json.JsonMapper
import java.io.File

/**
 * `.claude/conventions.json` 의 규칙을 컴파일된 클래스에 건다.
 *
 * 규칙의 문장·이유·예는 그 파일에 있고, 여기는 검사 종류(`check.kind`)마다 **검사하는 방법만** 안다.
 * 같은 파일을 `.claude/mods/convention-gate` 도 읽는다. 그쪽은 AI 가 파일을 쓰는 순간을, 여기는
 * 사람이 쓴 코드까지 전부를 맡는다.
 */
class ArchitectureTest {

    @TestFactory
    fun `코드가 규약을 지킨다`(): List<DynamicTest> {
        val main = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages(BASE_PACKAGE)
        val checker = ConventionChecker(BASE_PACKAGE, main)
        return RULES.map { rule ->
            dynamicTest("${rule.id} — ${rule.text}") {
                val violations = checker.check(rule)
                assertTrue(violations.isEmpty()) { rule.report(violations) }
            }
        }
    }

    /**
     * 층을 잘못 읽으면 검사할 클래스가 0개가 되어 모든 규칙이 **조용히 통과한다.**
     * 일부러 어긴 코드(`archfixture`)에서 규칙마다 하나 이상 잡혀야 검사가 살아 있다.
     */
    @TestFactory
    fun `규칙마다 일부러 어긴 코드를 잡는다`(): List<DynamicTest> {
        val checker = ConventionChecker(FIXTURE_PACKAGE, ClassFileImporter().importPackages(FIXTURE_PACKAGE))
        return RULES.map { rule ->
            dynamicTest(rule.id) {
                assertTrue(checker.check(rule).isNotEmpty()) {
                    "${rule.id} 가 $FIXTURE_PACKAGE 에서 아무것도 잡지 못했다 — 검사가 죽어 있다"
                }
            }
        }
    }

    private companion object {
        const val FIXTURE_PACKAGE = "archfixture"

        @Suppress("UNCHECKED_CAST")
        val CONVENTIONS = JsonMapper.builder().build()
            .readValue(File(".claude/conventions.json"), Map::class.java) as Map<String, Any?>

        val BASE_PACKAGE = CONVENTIONS["basePackage"] as String
        val LAYERS = CONVENTIONS.strings("layers").toSet()
        val RULES = (CONVENTIONS["rules"] as List<*>).map { Rule(it.asMap()) }
    }

    private class Rule(json: Map<String, Any?>) {
        val id = json["id"] as String
        val text = json["rule"] as String
        val why = json["why"] as String
        val ok = json["ok"]?.let { json.strings("ok") }.orEmpty()
        val check = json["check"].asMap()

        fun report(violations: List<String>) = buildString {
            appendLine("$id 위반 ${violations.size}건 — $text")
            appendLine("이유: $why")
            if (ok.isNotEmpty()) appendLine("예: ${ok.joinToString(", ")}")
            violations.forEach { appendLine("  - $it") }
            append("규칙에 없는 역할이라면 이름을 짓기 전에 묻고, 정해지면 .claude/conventions.json 에 이유와 함께 추가한다.")
        }
    }

    /** 검사 종류마다 방법 하나. 규칙이 모르는 종류를 쓰면 위반으로 돌려줘 조용히 넘어가지 않게 한다. */
    private class ConventionChecker(private val base: String, private val classes: JavaClasses) {

        fun check(rule: Rule): List<String> {
            val c = rule.check
            return when (val kind = c["kind"]) {
                "no-import" -> noImport(c["from"] as String, c.strings("to").toSet())
                "file-name" -> fileName(c["in"] as String, c.keysOf("endsWith"), c.keysOf("startsWith"), c.keysOf("except"))
                "banned-suffix" -> bannedSuffix(c["suffixes"].asMap())
                "interface-only" -> interfaceOnly(c["in"] as String, c.strings("endsWith"))
                "port-impl-name" -> portImplName(
                    c["portsIn"] as String, c.strings("portEndsWith"), c["implIn"] as String, c.keysOf("prefixes"),
                )
                "controller-deps" -> controllerDeps(
                    c["in"] as String, c["controllerEndsWith"] as String, c["typesFrom"] as String, c.strings("allowedEndsWith"),
                )
                else -> listOf("모르는 검사 종류다: $kind — ArchitectureTest 와 convention-gate 에 검사 방법을 더한다")
            }
        }

        /** `<base>.<모듈>.<층>` 의 층. 모듈 루트(공개 포트·값)는 층이 없다. */
        private fun layerOf(c: JavaClass): String? =
            c.packageName.takeIf { it.startsWith("$base.") }
                ?.removePrefix("$base.")?.split('.')?.getOrNull(1)
                ?.takeIf { it in LAYERS }

        /** `<모듈>/<층>/<파일>.kt`. 규칙의 `except` 가 이 모양으로 적는다. */
        private fun pathOf(c: JavaClass): String =
            c.packageName.removePrefix("$base.").replace('.', '/') + "/" + c.sourceCodeLocation.sourceFileName

        private fun inLayer(layer: String) = classes.filter { layerOf(it) == layer }

        private fun noImport(from: String, to: Set<String>) = inLayer(from).flatMap { c ->
            c.directDependenciesFromSelf.map { it.targetClass }
                .filter { target -> layerOf(target)?.let { it in to } == true }
                .map { "${c.name} → ${it.name}" }
        }.distinct()

        /** 클래스가 아니라 **소스 파일** 이름을 본다. `GenerationJobViews.kt` 처럼 한 파일에 여럿이 있어도 이름은 하나다. */
        private fun fileName(layer: String, ends: Set<String>, starts: Set<String>, except: Set<String>) =
            inLayer(layer).map(::pathOf).distinct()
                .filter { it !in except }
                .filter { path ->
                    val name = path.substringAfterLast('/').removeSuffix(".kt")
                    ends.none { name.endsWith(it) } && starts.none { name.startsWith(it) }
                }

        private fun bannedSuffix(suffixes: Map<String, Any?>) = classes
            .filter { !it.isAnonymousClass }
            .mapNotNull { c ->
                suffixes.entries.firstOrNull { c.simpleName.endsWith(it.key) }
                    ?.let { (suffix, why) -> "${c.name} — $suffix: $why" }
            }

        private fun interfaceOnly(layer: String, ends: List<String>) = inLayer(layer)
            .filter { c -> ends.any { c.simpleName.endsWith(it) } && !c.isInterface }
            .map { it.name }

        private fun portImplName(portsIn: String, portEnds: List<String>, implIn: String, prefixes: Set<String>) =
            inLayer(implIn).flatMap { c ->
                c.rawInterfaces
                    .filter { port -> layerOf(port) == portsIn && portEnds.any { port.simpleName.endsWith(it) } }
                    .filter { port -> prefixes.none { c.simpleName == it + port.simpleName } }
                    .map { port -> "${c.name} 는 ${port.simpleName} 의 구현이다 → {${prefixes.joinToString("|")}}${port.simpleName}" }
            }

        private fun controllerDeps(layer: String, controllerEnds: String, typesFrom: String, allowed: List<String>) =
            inLayer(layer).filter { it.simpleName.endsWith(controllerEnds) }.flatMap { c ->
                c.constructors.flatMap { it.rawParameterTypes }
                    .filter { layerOf(it) == typesFrom }
                    .filter { type -> allowed.none { type.simpleName.endsWith(it) } }
                    .map { "${c.simpleName} 가 ${it.simpleName} 를 받는다" }
            }.distinct()
    }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap(): Map<String, Any?> = this as Map<String, Any?>

private fun Map<String, Any?>.strings(key: String): List<String> = (this[key] as List<*>).map { it as String }

private fun Map<String, Any?>.keysOf(key: String): Set<String> = (this[key] as Map<*, *>?)?.keys?.map { it as String }?.toSet().orEmpty()
