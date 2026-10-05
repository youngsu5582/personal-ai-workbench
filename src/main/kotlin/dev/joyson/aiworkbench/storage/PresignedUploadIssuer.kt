package dev.joyson.aiworkbench.storage

import java.net.URI

/**
 * 클라이언트가 보관소에 **직접** 올릴 수 있는 주소를 발급한다.
 *
 * [PresignedUrlIssuer] 와 한 포트로 묶지 않는 이유는 **방향이 반대이고 위험도 반대**이기 때문이다.
 * 내주는 주소가 유출되면 남이 우리 것을 보지만, 받는 주소가 유출되면 남이 **우리 보관소에 쓴다.**
 * 한 인터페이스에 두면 "발급자가 있다" 가 두 권한을 동시에 뜻하게 되고, 읽기만 열고 싶을 때
 * 열 방법이 없어진다.
 *
 * [PresignedUrlIssuer] 와 같은 이유로 [FileStorage] 밖에 있다 — 모든 보관소가 할 수 있는 일이
 * 아니고, **없으면 그냥 빈이 없다.** 발급자가 없으면 업로드 경로가 열리지 않는다.
 *
 * ### 이 주소는 그 자체가 쓰기 권한이다
 *
 * 서명이 인증을 대신하므로 **발급 전에** "이 사람이 올려도 되는가" 를 판단해야 한다.
 * 발급 뒤에는 막을 수 없고, 수명이 곧 피해 범위다.
 */
fun interface PresignedUploadIssuer {

    /**
     * [key] 자리에 [contentType] 인 [contentLength] 바이트를 잠시 동안 올릴 수 있는 주소.
     *
     * [contentType] 은 **서명에 포함된다.** 올리는 쪽이 다른 값을 보내면 보관소가 거절하므로,
     * 서버가 바이트를 보지 못하는 이 경로에서 형식을 통제할 수 있는 **유일한 지점**이다.
     *
     * 다만 이것은 **선언이지 내용이 아니다.** 진짜 그 형식의 바이트인지는 올라온 뒤에 확인해야 한다.
     * [contentLength] 도 서명에 포함돼 발급 때 허용한 크기보다 큰 파일을 올릴 수 없다.
     *
     * 수명은 발급자가 안다 — [PresignedUrlIssuer.issue] 와 같은 이유다. 다만 읽기와 달리
     * 이 주소는 **전송이 끝날 때까지** 살아 있어야 해서, 느린 회선에서 짧은 수명이 먼저 문제가 된다.
     */
    fun issueUpload(key: String, contentType: String, contentLength: Long): URI
}

/**
 * [PresignedUploadIssuer] 를 필요해진 순간에 만든다.
 *
 * 모양과 이유는 [PresignedUrlIssuerFactory] 와 같다 — 빈 이름은 컨텍스트에서 유일해야 해서
 * `@Bean("s3")` 을 두 번 쓸 수 없고, 그래서 이름을 자기가 들고 `List` 로 받아 찾는다.
 */
interface PresignedUploadIssuerFactory {

    /** 어느 보관소의 발급자인가. `workbench.storage.provider` 값과 맞춘다. */
    val provider: String

    fun create(): PresignedUploadIssuer
}
