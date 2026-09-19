package dev.joyson.aiworkbench.generation.api

import dev.joyson.aiworkbench.generation.application.GeneratedFileReader
import dev.joyson.aiworkbench.ownership.OwnerContext
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
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
 * 매 요청마다 소유권을 확인한다. 이 점이 서명된 URL 과 다르다 —
 * 그쪽은 발급 시점에 한 번 확인하고, 그 뒤로는 서명이 확인을 대신한다.
 */
@RestController
@RequestMapping(GeneratedFileController.BASE_PATH)
class GeneratedFileController(
    private val generatedFileReader: GeneratedFileReader,
) {

    @GetMapping("/{uuid}")
    fun download(owner: OwnerContext, @PathVariable uuid: UUID): ResponseEntity<ByteArray> {
        val file = generatedFileReader.download(uuid, owner.userId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            // inline 이라 브라우저가 바로 그린다. <img src> 로 쓰려면 attachment 면 안 된다.
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(file.fileName).build().toString(),
            )
            .body(file.content)
    }

    companion object {
        const val BASE_PATH = "/api/files"

        /** 응답에 실을 주소. 만드는 곳이 하나여야 경로를 바꿀 때 한 군데만 고친다. */
        fun pathOf(uuid: UUID): String = "$BASE_PATH/$uuid"
    }
}
