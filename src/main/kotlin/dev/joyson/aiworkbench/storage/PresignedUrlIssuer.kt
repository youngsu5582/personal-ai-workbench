package dev.joyson.aiworkbench.storage

import java.net.URI

/**
 * 클라이언트가 보관소에서 **직접** 받아갈 수 있는 주소를 발급한다.
 *
 * [FileStorage] 에 넣지 않고 따로 둔 이유는, **모든 보관소가 할 수 있는 일이 아니기 때문**이다.
 * `LocalFileStorage` 는 서명할 대상도, HTTP 로 내줄 방법도 없다. 포트에 넣으면
 * "구현에 따라 없을 수도 있는 기능" 이 생기고, 그 `null` 의 뜻을 부르는 쪽이 전부 알아야 한다.
 *
 * 포트를 하나 더 두면 **없으면 그냥 빈이 없다.** 쓰는 쪽은 있으면 쓰고 없으면 기존 경로로 간다.
 *
 * ### 이 주소는 그 자체가 통행증이다
 *
 * 서명이 인증을 대신하므로, 발급 **전에** 소유권을 확인해야 한다. 발급 뒤에는 우리가 막을 수 없다.
 * 그래서 수명을 짧게 둔다 — 유출된 주소의 유효 기간이 곧 피해 범위다.
 */
fun interface PresignedUrlIssuer {

    /**
     * [key] 를 잠시 동안 받아갈 수 있는 주소.
     *
     * **수명은 발급자가 안다.** 부르는 쪽이 정하게 하면 보관소의 설정이 다른 모듈로 새고,
     * 호출마다 다른 값을 줄 수 있게 되어 "유출 창을 좁게 둔다" 는 정책이 한 곳에서 안 지켜진다.
     *
     * [fileName] 은 받는 쪽이 저장할 이름이다. 키가 내용 주소라 그대로 두면
     * 64자 해시가 파일명이 된다 — 보관소에 응답 헤더를 덮어써 달라고 부탁한다.
     */
    fun issue(key: String, fileName: String): URI
}

/**
 * [PresignedUrlIssuer] 를 필요해진 순간에 만든다.
 *
 * ### 왜 [FileStorageFactory] 와 모양이 다른가
 *
 * 그쪽은 빈 **이름**을 키로 하는 `Map` 이다. 여기서 같은 수를 쓸 수 없다 —
 * **빈 이름은 컨텍스트 전체에서 유일**해야 해서 `@Bean("s3")` 이 두 번 나올 수 없다.
 *
 * 그래서 이름을 빈이 아니라 **자기가 들고** 있고, `List` 로 받아 찾는다.
 * 이 저장소의 `ProviderRegistry` 가 `List<ExternalApiProvider>` 를 이름으로 뒤지는 것과 같은 모양이다.
 *
 * 성격도 다르다 — 보관소는 **반드시 하나**지만 발급자는 **없는 것이 정상**이다.
 */
interface PresignedUrlIssuerFactory {

    /** 어느 보관소의 발급자인가. `workbench.storage.provider` 값과 맞춘다. */
    val provider: String

    fun create(): PresignedUrlIssuer
}
