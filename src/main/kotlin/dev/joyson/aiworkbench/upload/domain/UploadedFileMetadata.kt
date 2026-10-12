package dev.joyson.aiworkbench.upload.domain

/**
 * 올린 파일에 대한 사실들. `uploaded_files.metadata` 에 JSON 한 칸으로 담긴다.
 *
 * 필드 이름을 결과물의 `FileMetadata` 와 맞춘다 — 두 파일이 같은 자리에 같은 모양으로 사실을 둔다.
 * 그 타입을 그대로 쓰지 않는 것은 generation 의 내부 타입이고, 가로·세로가 필수라 아직 그 값을 모르는
 * 업로드에 맞지 않기 때문이다. 가로·세로가 생기면 nullable 로 더한다 — 옛 행에는 없다.
 *
 * 허용 형식과 크기 상한은 여기서 검사하지 않는다. 그건 발급 정책([UploadSpec])이라 바뀔 수 있고,
 * 저장된 값을 읽을 때 검사하면 정책이 바뀌는 순간 옛 행을 못 읽는다.
 */
data class UploadedFileMetadata(
    val mimeType: String,
    val fileSize: Long,
) {
    init {
        require(fileSize > 0) { "파일 크기는 양수여야 한다" }
    }
}
