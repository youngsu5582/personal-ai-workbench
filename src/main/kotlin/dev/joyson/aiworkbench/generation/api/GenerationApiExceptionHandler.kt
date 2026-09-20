package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.application.UnknownImageSourceException
import dev.joyson.aiworkbench.generation.application.UnsupportedModelException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 이 모듈의 예외를 응답으로 옮긴다.
 *
 * 애플리케이션 계층이 HTTP 를 모르게 하려고 매핑을 여기 둔다 — 그쪽에 `@ResponseStatus` 를
 * 붙이면 Spring Web 이 도메인 쪽으로 번진다.
 *
 * 전역이 아니라 **이 컨트롤러에만** 건다. 전역으로 두면 다른 모듈의 예외까지 말없이 가로채,
 * 모듈 경계를 세워둔 의미가 응답 처리에서 무너진다.
 */
@RestControllerAdvice(assignableTypes = [GenerationJobController::class])
class GenerationApiExceptionHandler {

    /** 다시 보낸다고 달라지지 않으므로 4xx 다. */
    @ExceptionHandler(UnsupportedModelException::class)
    fun handleUnsupportedModel(e: UnsupportedModelException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message).apply {
            title = "다룰 수 없는 모델"
            setProperty("model", e.model)
            setProperty("availableProviders", e.available)
        }

    /**
     * 없는 것인지 남의 것인지 말하지 않는다 — 구분하면 그 uuid 의 존재가 새어 나간다.
     * 다시 보낸다고 달라지지 않으므로 4xx 다.
     */
    @ExceptionHandler(UnknownImageSourceException::class)
    fun handleUnknownImageSource(e: UnknownImageSourceException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message).apply {
            title = "쓸 수 없는 입력 이미지"
            setProperty("sourceUuid", e.sourceUuid.toString())
        }
}
