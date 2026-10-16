package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.application.UnknownFileSourceException
import dev.joyson.aiworkbench.generation.application.UnsupportedModelException
import dev.joyson.aiworkbench.storage.FileStorageException
import org.slf4j.LoggerFactory
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

    private val log = LoggerFactory.getLogger(javaClass)

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
    @ExceptionHandler(UnknownFileSourceException::class)
    fun handleUnknownFileSource(e: UnknownFileSourceException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message).apply {
            title = "쓸 수 없는 입력 이미지"
            setProperty("sourceSha256", e.sourceSha256)
        }

    /**
     * 올린 파일을 판정하려고 보관소에 갔다가 닿지 못했다. 요청이 아니라 이쪽 사정이라 5xx 다.
     * 원인 메시지에는 키·버킷 같은 내부 사정이 섞여 있어 응답에는 싣지 않고 로그에만 남긴다.
     */
    @ExceptionHandler(FileStorageException::class)
    fun handleStorageUnavailable(e: FileStorageException): ProblemDetail {
        log.warn("접수 중 보관소에 닿지 못했다: {}", e.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "입력 이미지를 지금 확인할 수 없다").apply {
            title = "보관소에 닿지 못함"
        }
    }
}
