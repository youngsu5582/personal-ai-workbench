package dev.joyson.aiworkbench.generation.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import dev.joyson.aiworkbench.generation.domain.option.DecisionEvidence
import dev.joyson.aiworkbench.generation.domain.option.DecisionOption
import dev.joyson.aiworkbench.generation.domain.option.DecisionQuestion
import dev.joyson.aiworkbench.generation.domain.option.FileSource
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.generation.domain.option.ImageOption
import dev.joyson.aiworkbench.generation.domain.option.TextGenerateOption

/**
 * 템플릿을 실행하면 무엇이 되는가 — 실행 명세.
 *
 * DB 가 조회 · 정렬에 쓰지 않고 실행할 때 통째로 읽는 값이라 JSON 한 칸(`spec`)에 담는다.
 * 함께 검증되는 값이기도 하다 — 받을 이미지 수는 옵션의 종류와 고정 참조 수에 달렸다.
 * 필드 이름이 곧 저장된 행의 계약이라, 바꾸면 기존 행을 못 읽는다.
 *
 * [option] 은 `/api/jobs` 의 `option` 과 같은 모양이고, 글 필드에 `{{name}}` 변수 자리를 둔다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TemplateSpec(
    val option: GenerationOption,
    /** 실행할 때 받을 이미지 수. 실행 요청은 정확히 이만큼 보내야 한다. */
    val inputImageCount: Int = 0,
    /** 실행 요청이 모델을 주지 않을 때 쓰는 모델. 없으면 실행할 때마다 골라야 한다. */
    val model: String? = null,
) {
    init {
        require(option.acceptsInputImages(inputImageCount)) { "이 옵션은 입력 이미지 ${inputImageCount}장을 받을 수 없다" }
        require(model == null || model.isNotBlank()) { "기본 모델을 비우려면 필드를 빼야 한다" }
    }

    /** 글 필드에 나오는 변수 이름. 처음 나온 순서다. 저장하지 않고 [option] 에서 매번 읽는다. */
    @get:JsonIgnore
    val variables: Set<String>
        get() = linkedSetOf<String>().also { names ->
            option.mapText { text -> VARIABLE.findAll(text).forEach { names += it.groupValues[1] }; text }
        }

    /**
     * 변수를 채우고 입력 이미지를 붙인 옵션을 만든다.
     *
     * 변수는 [variables] 와 키가 정확히 같아야 하고, 이미지는 [inputImageCount] 장이어야 한다.
     * 값은 한 번만 치환하므로 값 안의 `{{x}}` 는 그대로 남는다. 결과가 옵션의 규칙을 어기면
     * (치환 후 빈 prompt 등) [IllegalArgumentException] 이다.
     */
    fun fill(values: Map<String, String>, images: List<FileSource>): GenerationOption {
        val names = variables
        require(values.keys == names) { "필요한 변수와 전달된 변수가 일치해야 한다: 필요 $names" }
        require(images.size == inputImageCount) { "입력 이미지는 ${inputImageCount}장이어야 한다: ${images.size}장" }
        return option.mapText { text -> VARIABLE.replace(text) { values.getValue(it.groupValues[1]) } }
            .withInputImages(images)
    }

    private companion object {
        /** 이름은 영문자 또는 밑줄로 시작하고 영문자 · 숫자 · 밑줄만 쓴다. 중괄호 안에 공백을 두지 않는다. */
        val VARIABLE = Regex("\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)}}")
    }
}

/*
 * 아래는 명세가 옵션의 어느 자리를 다루는지다. 종류마다 `when` 으로 갈라, 종류가 늘면 빠진 곳이 컴파일 에러로 드러난다.
 */

/**
 * 사람이 쓰는 글 필드에만 [transform] 을 건다. 변수 자리를 찾고 채우는 범위가 이 목록이다.
 *
 * 식별자(질문 `name` · 선택지 `value` · 단계 `label`)는 답이 돌아올 때의 키라 건드리지 않는다.
 * 크기 · 품질 · 출력 형식은 저장할 때 검증해야 하는 값이라 글로 다루지 않는다.
 *
 * 새 옵션은 `copy` 로 만들어 생성자의 `require` 가 다시 돈다. 결과가 규칙을 어기면 [IllegalArgumentException] 이다.
 *
 * [transform] 은 사람이 템플릿을 읽는 순서로 불린다(지시문 → 프롬프트, 근거 → 질문). 변수 순서가 이 순서다.
 * 이름 붙인 인자는 적은 순서대로 평가되므로 인자 순서를 바꾸면 변수 순서도 바뀐다.
 */
private fun GenerationOption.mapText(transform: (String) -> String): GenerationOption = when (this) {
    is TextGenerateOption -> copy(instructions = instructions?.let(transform), prompt = transform(prompt))
    is ImageOption -> copy(prompt = transform(prompt))
    is DecisionOption -> copy(
        input = input.map { if (it is DecisionEvidence.Text) DecisionEvidence.Text(transform(it.text)) else it },
        questions = questions.map { it.mapText(transform) },
    )
}

private fun DecisionQuestion.mapText(transform: (String) -> String): DecisionQuestion = when (this) {
    is DecisionQuestion.Predicate -> copy(instructions = transform(instructions))
    is DecisionQuestion.Choice -> copy(
        instructions = transform(instructions),
        choices = choices.map { it.copy(description = transform(it.description)) },
    )
    is DecisionQuestion.Score -> copy(
        instructions = transform(instructions),
        levels = levels.map { it.copy(description = transform(it.description)) },
    )
}

/**
 * 실행 때 [count] 장의 이미지를 더 받을 수 있는가.
 *
 * 이미지 생성은 고정 참조와 입력을 합쳐 [ImageOption.MAX_SOURCES] 장까지다. 글 생성은 이미지를 받지 않는다.
 * 판정은 근거 수에 상한이 없다.
 */
private fun GenerationOption.acceptsInputImages(count: Int): Boolean = count >= 0 && when (this) {
    is TextGenerateOption -> count == 0
    is ImageOption -> sources.size + count <= ImageOption.MAX_SOURCES
    is DecisionOption -> true
}

/**
 * 실행 때 받은 이미지를 옵션에 붙인다. 이미지 생성은 참조 뒤에, 판정은 근거 뒤에 둔다.
 *
 * 글 생성에 이미지를 주면 [IllegalArgumentException] 이다. 받을 자리가 없는데 조용히 버리면
 * 사용자는 이미지가 쓰였다고 믿는다.
 */
private fun GenerationOption.withInputImages(images: List<FileSource>): GenerationOption = when (this) {
    is TextGenerateOption -> apply { require(images.isEmpty()) { "글 생성은 입력 이미지를 받지 않는다" } }
    is ImageOption -> copy(sources = sources + images)
    is DecisionOption -> copy(input = input + images.map { DecisionEvidence.Image(it) })
}
