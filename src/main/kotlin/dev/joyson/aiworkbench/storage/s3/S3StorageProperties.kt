package dev.joyson.aiworkbench.storage.s3

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * S3 호환 보관소 설정.
 *
 * **구현을 보관소마다 나누지 않는다.** Garage·MinIO·R2·AWS S3 의 차이는 [endpoint] 와 [pathStyleAccess] 뿐이라
 * 같은 어댑터가 전부 다룬다. 실측으로 확인한 것은 Garage 와 AWS SDK 기본 경로다.
 *
 * 값들이 `String?` 인 것은 **설정이 없는 상태를 표현하기 위해서**다. 다만 yaml 의
 * `"${VAR:}"` 는 변수가 없을 때 `null` 이 아니라 **빈 문자열**을 준다 — 그래서 읽는 쪽은
 * [orNull] 로 비어 있음을 없음으로 취급한다. 이 프로젝트는 같은 함정을
 * `@ConditionalOnProperty` 에서 한 번 밟았다.
 */
@ConfigurationProperties(prefix = "workbench.storage.s3")
data class S3StorageProperties(
    val bucket: String? = null,
    val region: String = "us-east-1",
    /**
     * AWS 가 아닌 곳(Garage·R2 등)을 가리킬 때 채운다. 비우면 AWS 기본 엔드포인트로 간다.
     *
     * **클라이언트가 볼 수 있는 주소여야 한다.** 서명 주소가 이 값으로 만들어져 그대로 밖에 나간다.
     * 내부망 이름을 넣으면 앱은 잘 붙는데 브라우저는 그 주소를 못 열어,
     * 302 와 `previewUrl` 이 **조용히** 깨진다. 앱과 클라이언트의 경로가 갈리는 배치라면
     * 발급 전용 공개 주소를 따로 둬야 한다.
     */
    val endpoint: String? = null,
    /**
     * 비워두면 SDK 의 기본 탐색(환경변수·인스턴스 역할 등)에 맡긴다.
     * 배포에서 역할 기반 인증을 쓰려면 여기를 비우는 것이 정답이다.
     */
    val accessKey: String? = null,
    val secretKey: String? = null,
    /**
     * `bucket.host` 가 아니라 `host/bucket` 으로 부른다.
     *
     * 자가호스팅 구현은 대개 이쪽이 필요하다 — 가상 호스트 방식은 버킷마다 DNS 가 있어야 하는데
     * 로컬 컨테이너에는 없다.
     */
    val pathStyleAccess: Boolean = false,
) {
    companion object {
        /** 빈 문자열을 "설정되지 않음" 으로 본다. */
        fun String?.orNull(): String? = this?.takeIf { it.isNotBlank() }
    }
}
