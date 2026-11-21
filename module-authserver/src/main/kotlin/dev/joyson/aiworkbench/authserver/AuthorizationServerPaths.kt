package dev.joyson.aiworkbench.authserver

/**
 * 인가 서버가 다른 모듈에 알리는 자기 경로.
 *
 * Google 로그인을 마친 쪽이 "인가 엔드포인트로 가다가 온 로그인인가" 를 가릴 때 쓴다.
 * 실제 엔드포인트가 이 값과 다르면 기동에서 실패한다.
 */
object AuthorizationServerPaths {
    const val AUTHORIZATION = "/oauth2/authorize"
}
