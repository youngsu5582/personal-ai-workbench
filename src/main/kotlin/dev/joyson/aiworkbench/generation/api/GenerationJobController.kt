package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.application.GenerationCommand
import dev.joyson.aiworkbench.generation.application.GenerationJobWriter
import dev.joyson.aiworkbench.generation.domain.GenerationJob
import dev.joyson.aiworkbench.generation.domain.option.GenerationOption
import dev.joyson.aiworkbench.ownership.OwnerContext
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/jobs")
class GenerationJobController(
    private val generationJobWriter: GenerationJobWriter
) {

    @PostMapping
    fun generate(owner: OwnerContext, @Valid @RequestBody request: GenerationRequest): ResponseEntity<GenerationResponse> {
        val job = generationJobWriter.generate(owner.userId, request.toCommand())
        // 접수 확인이므로, 202 반환
        return ResponseEntity.accepted().body(GenerationResponse(job.uuid))
    }
}

/**
 * 생성 요청 본문.
 *
 * 제약을 여기 두는 이유는 응답 코드 때문이다.
 * 요청 바인딩 단계에서 걸리면 400 이지만, 서비스까지 들어가서 터지면 500 이 된다.
 * 도메인([GenerationJob])에도 같은 불변식이 있는데 그쪽은 최후 방어다 —
 * 거기서 터지면 요청이 아니라 우리 코드의 버그라는 뜻이다.
 *
 * 상한 4 는 `GenerationJob.MAX_TASK_COUNT` 와 같아야 한다.
 * 애노테이션 인자는 컴파일 타임 상수여야 해서 참조할 수 없고, 테스트가 대신 지킨다.
 */
data class GenerationRequest(
    @field:NotNull
    val option: GenerationOption?,
    @field:NotBlank
    val model: String?,
    @field:Min(1)
    @field:Max(4)
    val taskCount: Int,
) {
    fun toCommand(): GenerationCommand {
        return GenerationCommand(
            option = requireNotNull(option),
            model = requireNotNull(model),
            taskCount = taskCount,
        )
    }
}

data class GenerationResponse(
    val uuid: UUID
)
