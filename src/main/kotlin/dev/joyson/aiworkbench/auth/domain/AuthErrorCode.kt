package dev.joyson.aiworkbench.auth.domain

/**
 * auth 모듈이 요청을 거부할 때 쓰는 코드와 사유.
 *
 * 흩어진 문자열 리터럴을 한곳에 모은 이유는 두 가지다.
 * - `code` 는 로그·모니터링에서 거부 사유를 집계하는 키다. 오타가 나면 조용히 다른 사유가 된다.
 * - 같은 상황에 서로 다른 문구가 붙는 것을 막는다.
 *
 * `code` 는 OAuth2 표준 에러 코드(RFC 6749 §5.2)가 아니라 이 서비스가 정한 값이다.
 * 표준 코드와 겹치지 않도록 구체적인 이름을 쓴다.
 */
enum class AuthErrorCode(val code: String, val message: String) {


    /* 내가 발급한 토큰의 검증 */
    INVALID_AUDIENCE("invalid_audience", "이 토큰은 다른 대상용이다"),

    ;
}
