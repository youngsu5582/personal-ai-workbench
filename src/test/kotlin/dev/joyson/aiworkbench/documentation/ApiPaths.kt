package dev.joyson.aiworkbench.documentation

import com.tngtech.archunit.core.domain.JavaClasses
import org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation
import org.springframework.http.server.PathContainer
import org.springframework.stereotype.Controller
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.util.pattern.PathPattern
import org.springframework.web.util.pattern.PathPatternParser

/**
 * 컨트롤러가 여는 경로. 클래스의 `@RequestMapping` 접두어와 메서드의 `@GetMapping` 등을 잇는다.
 *
 * `const val` 경로는 컴파일 때 애노테이션 값으로 박혀 문자열로 읽힌다. `@GetMapping` 을 `@RequestMapping` 으로
 * 합쳐 읽는 것은 Spring 이 하고, 접두어와 잇는 규칙도 Spring 의 것을 쓴다.
 * 쿼리 파라미터는 경로가 아니라 매개변수에 있어 여기에 없다.
 */
internal class ApiPaths(private val patterns: List<PathPattern>) {

    /** 경로 변수 자리(`{uuid}`)는 아무 값이나 받으므로 `/api/jobs/123` 도 `/api/jobs/{uuid}` 에 맞는다. */
    fun opens(path: String): Boolean {
        val container = PathContainer.parsePath(path)
        return patterns.any { it.matches(container) }
    }

    companion object {
        fun of(classes: JavaClasses) = ApiPaths(
            classes
                .filter { it.isAnnotatedWith(Controller::class.java) || it.isMetaAnnotatedWith(Controller::class.java) }
                .flatMap { pathsOf(it.reflect()) }
                .filter { it.isNotEmpty() }
                .map(PathPatternParser.defaultInstance::parse),
        )

        private fun pathsOf(controller: Class<*>): List<String> {
            val prefixes = pathsIn(findMergedAnnotation(controller, RequestMapping::class.java))
            return controller.declaredMethods
                .mapNotNull { findMergedAnnotation(it, RequestMapping::class.java) }
                .flatMap { mapping -> prefixes.flatMap { prefix -> pathsIn(mapping).map { MATCHER.combine(prefix, it) } } }
        }

        /** 경로를 적지 않은 매핑(`@PostMapping`)은 접두어 그대로다. */
        private fun pathsIn(mapping: RequestMapping?): List<String> =
            mapping?.path?.toList().orEmpty().ifEmpty { listOf("") }

        private val MATCHER = AntPathMatcher()
    }
}
