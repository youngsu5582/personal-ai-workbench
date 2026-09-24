package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.application.FileDownload
import dev.joyson.aiworkbench.generation.application.GeneratedFileReader
import dev.joyson.aiworkbench.ownership.OwnerContext
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 결과물을 내려보낸다.
 *
 * Job 밑이 아니라 별도 경로인 이유: 파일을 Job 안에서 가리킬 번호가 없다.
 * Task 를 거쳐야 하므로 `/api/jobs/{job}/tasks/{taskSeq}/files/...` 처럼 세 단이 되는데,
 * 파일의 `uuid` 는 그 자체로 유일해 한 단으로 끝난다.
 *
 * **매 요청마다 소유권을 확인한다.** 보관소가 서명된 주소를 줄 수 있으면 확인 뒤에 302 로 넘기고,
 * 못 주면 우리가 읽어 내보낸다. 어느 쪽이든 이 주소와 인가 지점은 그대로라,
 * 보관소를 바꿔도 클라이언트가 부르는 곳은 안 바뀐다.
 */
@RestController
@RequestMapping(GeneratedFileController.BASE_PATH)
class GeneratedFileController(
    private val generatedFileReader: GeneratedFileReader,
) {

    @GetMapping("/{uuid}")
    fun download(owner: OwnerContext, @PathVariable uuid: UUID): ResponseEntity<*> =
        when (val download = generatedFileReader.download(uuid, owner.uuid)) {
            null -> ResponseEntity.notFound().build<Void>()

            // 302 다. 301 이면 브라우저가 캐시하는데, 그 주소는 곧 만료된다.
            is FileDownload.Redirect ->
                ResponseEntity.status(HttpStatus.FOUND).location(download.url).build<Void>()

            is FileDownload.Streamed ->
                ResponseEntity.ok()
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
        fun pathOf(uuid: UUID): String = "$BASE_PATH/$uuid"
    }
}
