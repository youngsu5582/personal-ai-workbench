package dev.joyson.aiworkbench.storage

import java.util.UUID

/**
 * 소유자·내용·형식으로 정하는 보관소 주소.
 *
 * 키의 본체는 바이트의 SHA-256 이다. 그래서 셋이 따라온다.
 *
 * - **키에 "몇 번째" 나 "무슨 종류" 같은 축이 없다.** 썸네일·리사이즈가 생겨도 자기 바이트로 자기 자리를
 *   가지므로, 그것이 무엇인지는 키가 아니라 DB 행이 안다.
 * - **보관이 멱등하다.** 같은 내용을 다시 써도 고아가 생기지 않는다.
 * - **이 키를 쓰는 버킷은 public-read 로 열지 않는다.** 바이트를 가진 사람은 키를 계산할 수 있다.
 *
 * 해시와 실제 내용이 일치하는지는 호출자가 보장한다. 사용자에게서 해시를 받았다면
 * 형식 검증만으로는 부족하고, 업로드한 바이트의 해시도 검증해야 한다.
 */
@JvmInline
value class BlobKey private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        private const val FANOUT_DEPTH = 2
        private const val FANOUT_SEGMENTS = 2

        fun of(workspaceUuid: UUID, digest: Sha256, mimeType: String): BlobKey =
            key("workspaces/$workspaceUuid/blobs", digest, mimeType)

        /** 업로드 쓰기 권한으로 생성 결과물을 덮어쓰지 못하게 자리를 나눈다. */
        fun uploaded(workspaceUuid: UUID, digest: Sha256, mimeType: String): BlobKey =
            key("workspaces/$workspaceUuid/uploads", digest, mimeType)

        private fun key(prefix: String, digest: Sha256, mimeType: String): BlobKey {
            val hex = digest.hex

            // 로컬 디스크의 한 디렉토리에 파일이 몰리지 않게 해시 앞 네 글자로 나눈다.
            val fanout = hex.chunked(FANOUT_DEPTH).take(FANOUT_SEGMENTS).joinToString("/")

            // 확장자는 사람이 보관소를 열어볼 때 파일 형식을 알아보게 한다.
            val extension = mimeType.substringAfterLast('/', "").ifBlank { "bin" }
            return BlobKey("$prefix/$fanout/$hex.$extension")
        }
    }
}
