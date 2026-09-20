package dev.joyson.aiworkbench.generation.domain.option

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.UUID

/**
 * 고칠 이미지가 어디 있는지 말하는 방법.
 *
 * 구현체가 하나뿐인데도 판별자를 두는 이유는 **이 값이 저장되기 때문**이다.
 * jsonb 에 들어간 뒤에 모양을 바꾸려면 기존 행을 고쳐야 하는데, 판별자 하나를 미리 두는 값은
 * 그보다 훨씬 싸다 — [GenerationOption] 도 구현체가 하나일 때 같은 판단을 먼저 했다.
 *
 * 종류를 가르는 축은 **어느 테이블이 그 파일을 아는가** 다. 같은 바이트여도 우리가 만든 것과
 * 바깥에서 들어온 것은 사는 곳이 다르고, 참조가 그 사실을 들고 있어야 푸는 쪽이 갈 곳을 안다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ImageSource.Generated::class, name = "generated"),
)
sealed interface ImageSource {

    /** 가리키는 파일의 식별자. 어디서 찾을지는 구현체가 말한다. */
    val uuid: UUID

    /** 이 서비스가 만들어 둔 결과물. `generated_files` 의 한 행이다. */
    data class Generated(override val uuid: UUID) : ImageSource
}
