package dev.joyson.aiworkbench.generation.domain.option

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dev.joyson.aiworkbench.generation.domain.GenerationJobType

/**
 * 생성 요청의 입력. 생성 종류마다 필요한 값이 다르다.
 *
 * `sealed` 인 이유는 when 이 빠짐없이 처리되도록 컴파일러가 강제하기 때문이다.
 * Provider adapter 가 종류별로 분기할 때 새 종류를 추가하면 처리 안 한 곳이 컴파일 에러로 드러난다.
 *
 * 구현체가 같은 패키지에 있어야 하는 것은 Kotlin 의 sealed 제약이다.
 * 그래서 인터페이스와 구현을 `option` 패키지에 함께 둔다.
 *
 * JSON 한 컬럼에 저장하므로 역직렬화 시 종류를 알 수 있어야 한다 —
 * 그래서 `type` 필드로 판별한다. 이 이름은 DB 에 그대로 들어가므로 바꾸면 기존 행을 못 읽는다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = TextToImageOption::class, name = "text-to-image"),
)
sealed interface GenerationOption {
    /**
     * 생성 종류. JSON 의 판별자이자 객체의 실제 프로퍼티다(`EXISTING_PROPERTY`).
     *
     * 판별자는 Jackson 이 직접 쓰므로(`As.PROPERTY` 기본값) 이 프로퍼티는 직렬화에서 제외한다.
     * 제외하지 않으면 `type` 키가 두 번 나오고, `EXISTING_PROPERTY` 로 바꾸면
     * 역직렬화 때 Jackson 이 이 값을 객체에 넣으려다 실패한다(setter 가 없는 계산 프로퍼티라서).
     *
     * 생성자 파라미터로 열지 않는 이유: `TextToImageOption("고양이", TEXT_TO_VIDEO)` 처럼
     * 객체가 자기 타입을 거짓말할 수 있게 된다.
     */
    @get:JsonIgnore
    val type: GenerationJobType
}
