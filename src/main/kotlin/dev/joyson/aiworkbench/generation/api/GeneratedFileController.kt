package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.application.FileDownload
import dev.joyson.aiworkbench.generation.application.GeneratedFileService
import dev.joyson.aiworkbench.generation.domain.FileKind
import dev.joyson.aiworkbench.ownership.WorkspaceContext
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import jakarta.validation.constraints.Pattern

/**
 * 결과물을 내려보낸다.
 *
 * Job 밑이 아니라 별도 경로인 이유: 파일을 Job 안에서 가리킬 번호가 없다.
 * Task 를 거쳐야 하므로 `/api/jobs/{job}/tasks/{taskSeq}/files/...` 처럼 세 단이 되는데,
 * 내용 해시로 파일을 가리켜 작업 계층을 URL 에 싣지 않는다.
 *
 * **매 요청마다 소유권을 확인한다.** 보관소가 서명된 주소를 줄 수 있으면 확인 뒤에 302 로 넘기고,
 * 못 주면 우리가 읽어 내보낸다. 어느 쪽이든 이 주소와 인가 지점은 그대로라,
 * 보관소를 바꿔도 클라이언트가 부르는 곳은 안 바뀐다.
 */
@RestController
@RequestMapping(GeneratedFileController.BASE_PATH)
class GeneratedFileController(
    private val generatedFileService: GeneratedFileService,
) {

    /**
     * 이 서비스가 만든 결과 파일을 종류별로 최신순으로 나누어 준다.
     *
     * [kind] 를 비워 두지 않는다. 종류마다 그리는 방식이 달라 한 목록에 섞어 받을 일이 없고,
     * 종류가 늘어도 이미 쓰는 쪽이 받는 것이 바뀌지 않는다.
     */
    @GetMapping
    fun list(
        scope: WorkspaceContext,
        @RequestParam kind: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "24") size: Int,
    ): GeneratedFileListResponse {
        val fileKind = FileKind.entries.firstOrNull { it.value == kind }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 종류다: $kind")
        if (page < 0 || size !in 1..60 || page.toLong() * size > Int.MAX_VALUE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 페이지 요청입니다.")
        }
        return generatedFileService.listOwned(scope.workspaceUuid, fileKind, page, size).toResponse()
    }

    @GetMapping("/{sha256}")
    fun download(scope: WorkspaceContext, @PathVariable @Pattern(regexp = "[0-9a-f]{64}") sha256: String): ResponseEntity<*> =
        when (val download = generatedFileService.download(sha256, scope.workspaceUuid)) {
            null -> ResponseEntity.notFound().build<Void>()

            // 302 다. 301 이면 브라우저가 캐시하는데, 그 주소는 곧 만료된다.
            is FileDownload.Redirect ->
                ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .location(download.url).build<Void>()

            is FileDownload.Streamed ->
                ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .contentType(MediaType.parseMediaType(download.contentType))
                    // inline 이라 브라우저가 바로 그린다. <img src> 로 쓰려면 attachment 면 안 된다.
                    .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(download.fileName).build().toString(),
                    )
                    .body(download.content)
        }

    companion object {
        const val BASE_PATH = "/api/files"

        /** 응답에 실을 주소. 만드는 곳이 하나여야 경로를 바꿀 때 한 군데만 고친다. */
        fun pathOf(sha256: String): String = "$BASE_PATH/$sha256"
    }
}
