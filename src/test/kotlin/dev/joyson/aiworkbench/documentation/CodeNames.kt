package dev.joyson.aiworkbench.documentation

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses

/**
 * 코드가 아는 이름. ArchUnit 이 읽은 우리 클래스와, 우리 코드가 **쓰는** 남의 클래스.
 *
 * `Long` · `ApplicationModules` 처럼 남의 클래스도 우리가 쓰고 있으면 아는 이름이다 —
 * 아무도 쓰지 않는 이름이 문서에만 있는 것이 문제다. 남의 클래스는 안을 들여다보지 않는다.
 */
internal class CodeNames(classes: JavaClasses) {
    private val ours = classes.groupBy { it.simpleName }
    private val byName = classes.associateBy { it.name }
    private val used = classes
        .flatMap { c -> c.directDependenciesFromSelf.map { it.targetClass.simpleName } }
        .toSet()

    /** 확장 함수는 파일 클래스의 static 메서드가 되고, 첫 매개변수가 받는 쪽이다. (받는 타입, 메서드) */
    private val extensions = classes.flatMap { c ->
        c.methods.mapNotNull { m -> m.rawParameterTypes.firstOrNull()?.let { it.simpleName to m.name } }
    }.toSet()

    fun isOurs(type: String) = type in ours

    fun knows(type: String) = type in ours || type in used

    fun hasMember(type: String, member: String): Boolean =
        ours[type].orEmpty().any { it.declares(member) } ||
            extensions.any { (receiver, jvmName) -> receiver == type && jvmName.isCompiledFrom(member) }

    private fun JavaClass.declares(member: String): Boolean {
        val owners = listOfNotNull(this, byName["$name\$Companion"])
        return owners.any { o ->
            (o.allMethods.map { it.name } + o.allFields.map { it.name }).any { it.isCompiledFrom(member) }
        }
    }

    /**
     * Kotlin 은 프로퍼티를 getter 로, `internal` 멤버를 `이름$모듈` 로, value class 가 걸린 함수를
     * `이름-impl` · `이름-해시` 로 옮긴다. companion 멤버는 [declares] 가 따로 본다.
     *
     * 꼬리를 떼고 비교하지 않는다. 백틱 이름(`SHA-256 은 …`)에는 `-` 가 그대로 들어가서, 떼면 있는 이름이 잘린다.
     */
    private fun String.isCompiledFrom(member: String): Boolean {
        val accessor = member.replaceFirstChar { it.uppercase() }
        return listOf(member, "get$accessor", "is$accessor").any { this == it || startsWith("$it-") } ||
            startsWith("$member$")
    }
}
