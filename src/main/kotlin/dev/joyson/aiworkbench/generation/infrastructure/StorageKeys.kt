package dev.joyson.aiworkbench.generation.infrastructure

import dev.joyson.aiworkbench.generation.domain.MimeTypes
import dev.joyson.aiworkbench.generation.domain.Sha256
import java.util.UUID

/**
 * 보관소 키를 만든다. 경로에 소유자와 내용이 드러나는 것은 도메인 지식이라 보관소가 아니라 여기가 정한다.
 *
 * 키의 본체는 바이트의 SHA-256 이다. 그래서 셋이 따라온다.
 *
 * - **키에 "몇 번째" 나 "무슨 종류" 같은 축이 없다.** 썸네일·리사이즈가 생겨도 자기 바이트로 자기 자리를
 *   가지므로, 그것이 무엇인지는 키가 아니라 DB 행이 안다.
 * - **보관이 멱등하다.** 같은 내용을 다시 써도 고아가 생기지 않는다.
 * - **이 키를 쓰는 버킷은 public-read 로 열지 않는다.** 바이트를 가진 사람은 키를 계산할 수 있다.
 *
 * `generated_files.storage_key` 에 유니크를 걸지 않는 이유는 충돌이 흔해서가 아니라 — 모델 생성으로는
 * 사실상 나오지 않는다 — **지킬 불변식이 아니기 때문**이다. 원하는 것은 "한 결과물당 한 행" 이지
 * "한 blob 당 한 행" 이 아니다. 걸면 정당한 경우에만 터진다: 파생물은 순수 함수라 다시 돌리면 같은 바이트다.
 * 중복 제거를 진짜로 하게 되면 `blobs(digest PK, ...)` 를 따로 두는 것이 유니크의 제자리다.
 *
 * 소유자 접두사의 대가는 `jobs/{jobUuid}/` 로 작업 결과를 한 번에 지우던 수단이다. 고아가 job 과 무관하게
 * 흩어지므로, 정리는 소유자 접두사를 훑어 대응 행이 없는 것을 지우는 mark-and-sweep 이 된다.
 */
object StorageKeys {

    private const val FANOUT_DEPTH = 2
    private const val FANOUT_SEGMENTS = 2

    fun generatedFile(ownerUuid: UUID, digest: Sha256, mimeType: String): String {
        val hex = digest.hex

        // 소유자로 먼저 가른다 — 계정 정리가 접두사 하나로 끝나고, 남의 바이트 보관 여부를 키로 떠볼 수 없다.
        val prefix = "users/$ownerUuid/blobs"

        // digest 앞을 잘라 디렉토리를 판다. 로컬 디스크에서 한 디렉토리의 항목 수를 줄이려는 것이고,
        // S3 로 가면 없어도 된다. 별도 값이 아니라 digest 에서 잘라내므로 키는 여전히 내용이 정한다.
        val fanout = hex.chunked(FANOUT_DEPTH).take(FANOUT_SEGMENTS).joinToString("/")

        // 확장자는 사람을 위한 것이다 — 없으면 `ls` 와 콘솔에 정체불명의 64자 hex 만 남는다.
        // 대가로 키가 형식에도 걸리지만, 형식이 바이트에서 나오는 도메인이라 갈릴 경로가 없다.
        val name = "$hex.${MimeTypes.extensionOf(mimeType)}"

        return "$prefix/$fanout/$name"
    }
}
