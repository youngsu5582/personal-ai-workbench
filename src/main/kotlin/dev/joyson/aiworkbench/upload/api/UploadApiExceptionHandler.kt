package dev.joyson.aiworkbench.upload.api

import dev.joyson.aiworkbench.storage.FileStorageException
import dev.joyson.aiworkbench.upload.application.InvalidUploadException
import dev.joyson.aiworkbench.upload.application.UploadStateChangedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** 업로드 유스케이스의 실패만 HTTP 응답으로 옮긴다. */
@RestControllerAdvice(assignableTypes = [UploadController::class])
class UploadApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidUploadException::class)
    fun handleInvalidUpload(e: InvalidUploadException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)

    @ExceptionHandler(UploadStateChangedException::class)
    fun handleStateChanged(e: UploadStateChangedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message)

    /** 보관소 키나 내부 응답은 로그에만 남기고 파일 확인을 나중에 다시 시도하게 한다. */
    @ExceptionHandler(FileStorageException::class)
    fun handleStorageUnavailable(e: FileStorageException): ProblemDetail {
        log.warn("중복 업로드 확인 중 보관소에 닿지 못했다: {}", e.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "업로드한 파일을 지금 확인할 수 없다")
    }
}
