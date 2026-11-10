# ADR 0005. 워크벤치 안의 모듈로 OAuth 2.1 인가 서버를 둔다

- 상태: 제안 (2026-11-10)
- 범위: 사람의 로그인 · 토큰 발급과 검증 · 콘솔 로그인 유지 · CLI 와 MCP 클라이언트의 연결
- 관련 결정: [ADR 0004](0004-service-ownership.md) — 서비스 API 키

## 결정

- 워크벤치가 OAuth 2.1 인가 서버를 겸한다. 별도 서버가 아니라 같은 앱의 `authserver` 모듈이다. 구현은 Spring Security 에 합쳐진 Spring Authorization Server(`org.springframework.security:spring-security-oauth2-authorization-server`)다.
- 사람의 로그인은 인가 코드 + PKCE 로만 받는다. implicit · password grant 는 쓰지 않는다. Google 은 인가 서버의 로그인 화면 뒤에서 사용자를 확인하는 수단으로 남는다(연합 로그인).
- 콘솔은 **토큰 중개 백엔드** 방식으로 로그인한다. 같은 앱의 콘솔 백엔드가 OAuth 클라이언트로서 인가 코드를 토큰으로 바꾸고, 리프레시 토큰은 스크립트가 읽지 못하는 쿠키에 둔다. 브라우저는 접근 토큰만 메모리에 갖는다. 새로고침하거나 접근 토큰이 만료되면 그 쿠키로 새 접근 토큰을 받는다.
- 리프레시 토큰은 쓸 때마다 새로 발급한다(회전). 그래서 발급한 인가와 토큰을 DB 에 둔다 — 서명만으로 검증하는 리프레시 토큰은 회전하거나 하나만 폐기할 수 없다.
- 접근 토큰은 비대칭 키(ES256)로 서명하고 공개키를 JWKS 로 내보낸다. API 는 JWKS 로만 검증한다. 같은 앱이어도 리소스 서버는 인가 서버의 내부를 부르지 않는다.
- 접근 토큰의 `aud` 는 API 의 정본 주소다(RFC 8707 의 resource 와 같은 값). `sub` 는 지금처럼 사용자 UUID 다.
- 발급자(`iss`)는 지금 주소인 `https://workbench.youngsu5582.today` 다. 인가 서버를 떼어 낼 때는 같은 호스트에서 인가 경로(`/oauth2/**` · `/.well-known/oauth-authorization-server` · 로그인 경로)만 새 서버로 보낸다. 발급자를 바꾸지 않아 이미 등록된 클라이언트와 토큰 검증 설정이 그대로 남는다.
- 서비스 API 키(ADR 0004)는 그대로 둔다. 사람을 대신하지 않는 서버 간 호출이라 OAuth 흐름에 넣지 않는다.
- 지금의 자체 로그인(handoff 쿠키 + HS256 토큰)은 콘솔이 새 흐름으로 옮긴 뒤 걷어 낸다.

## 문제와 사용자 영향

- 콘솔은 토큰을 메모리에만 두고 연장 수단이 없어, 새로고침할 때마다 그리고 15분마다 Google 로그인을 다시 거친다.
- 로그인이 자체 흐름(handoff 쿠키)이라, 표준 OAuth 클라이언트인 CLI 와 MCP 클라이언트는 사람을 대신해 워크벤치에 로그인할 방법이 없다. MCP 인가 명세는 인가 서버가 OAuth 2.1 을 따르고 메타데이터로 발견되기를 요구한다.
- 접근 토큰이 대칭키(HS256)로 서명돼, 검증하려면 서명 비밀을 나눠 가져야 한다. 인가 서버를 떼어 내거나 다른 리소스가 토큰을 검증하면 비밀이 퍼진다.

## 제약

- 단일 노드 홈서버이고 사실상 사용자가 한 명이다. 서버 · DB · Secret 의 수를 늘리지 않는다.
- 공개 배포 중이라 전환하는 동안 콘솔 로그인이 끊기지 않아야 한다.
- 브라우저에 두는 토큰은 페이지의 스크립트가 읽을 수 없어야 한다. 지금 접근 토큰을 메모리에만 두는 원칙을 리프레시 토큰에도 지킨다.
- API 는 무상태 Bearer 토큰 검증을 유지한다. 콘솔만 다른 인증 방식(세션 쿠키)을 쓰게 하지 않는다.

## 선택한 해법

```text
브라우저(콘솔) ── 접근 토큰(메모리) ──────────────▶ 워크벤치 API (리소스 서버, JWKS 로 검증)
   │ 새로고침 · 만료
   └─ 리프레시 쿠키 ─▶ 콘솔 백엔드(토큰 중개) ─▶ authserver  /oauth2/token
                                                │
                       로그인 화면 ─────────────┴─▶ Google (사용자 확인)

CLI · MCP 클라이언트 ── 인가 코드 + PKCE + resource ─▶ authserver   (2단계)
내 다른 서비스 ──────── 서비스 API 키 ──────────────▶ 워크벤치 API (ADR 0004)
```

- `authserver` 모듈이 등록된 클라이언트 · 발급한 인가 · 사용자 동의의 테이블과 서명 키를 갖는다. 다른 모듈은 그 테이블을 읽지 않는다.
- 로그인한 Google 정체성을 워크벤치 사용자로 바꾸는 일은 지금처럼 `user` 모듈의 포트가 맡는다. 비활성 사용자는 토큰을 받지 못하고, 이미 받은 토큰도 요청마다 막힌다.
- `auth` 모듈은 리소스 서버 쪽을 맡는다 — JWKS 검증, 서비스 키 인증, 요청의 소유자 · 서비스 해석.
- 리프레시 쿠키는 HttpOnly · Secure · SameSite=Strict 이고, 경로를 갱신 엔드포인트로 한정한다.
- 서명 키는 홈서버 Secret 으로 두고, 교체할 때는 새 키를 JWKS 에 먼저 올린 뒤 서명 키를 바꾼다. 옛 키는 그 키로 서명한 토큰이 모두 만료된 뒤 내린다.

### 단계

1. 콘솔 — 인가 서버 · JWKS · 토큰 중개 백엔드 · 리프레시 회전. 기존 자체 로그인을 걷어 낸다.
2. CLI · MCP 클라이언트 — 보호 리소스 메타데이터(RFC 9728)를 채우고, `resource` 로 받은 토큰만 받는다. 클라이언트 등록은 Client ID Metadata Documents 를 먼저 지원하고, 동적 등록(RFC 7591)은 그것을 못 쓰는 클라이언트를 위해서만 둔다. 인가 응답에 `iss` 를 싣는다(RFC 9207).
3. 권한 — scope 를 나누고 동의 화면을 둔다.

### 전환

새 흐름과 기존 자체 로그인을 함께 받는 기간을 둔다. 리소스 서버는 그동안 두 발급 방식의 토큰을 모두 검증하고, 콘솔이 새 흐름으로 옮긴 배포에서 기존 검증과 handoff 경로를 지운다.

## 선택하지 않은 대안

| 대안 | 선택하지 않은 이유 |
|---|---|
| 자체 리프레시 쿠키(서명만으로 검증하는 토큰) | 빨리 만들 수 있지만 비표준이라 CLI · MCP 클라이언트가 쓸 수 없고, 회전과 하나만 폐기하기가 안 된다. 표준으로 옮길 때 모두 버린다 |
| 인가 서버를 별도 서버로 | 단일 노드 · 1인 사용에서 서버 · DB 연결 · Secret 이 늘어나는 부담이 독립 배포와 장애 분리의 이득보다 크다. 분리는 같은 호스트의 경로 라우팅으로 나중에 할 수 있다 |
| 기성 인가 서버(Keycloak · Authentik 등) | 표준 기능은 많지만 운영 부담이 가장 크고, 사용자 · 서비스 모델(ADR 0004)과 따로 놀아 둘을 잇는 코드가 생긴다 |
| 콘솔을 브라우저 공개 클라이언트로 | 새로고침을 견디려면 리프레시 토큰을 브라우저 저장소에 둬야 하는데, 그러면 페이지의 스크립트가 읽을 수 있다 |
| 콘솔 BFF(콘솔은 세션 쿠키로 API 를 부른다) | API 가 콘솔에만 쿠키 인증과 CSRF 방어를 받게 되어 인증 방식이 둘로 갈린다 |
| HS256 서명 유지 | 같은 앱이라 검증은 되지만 공개키로 내보낼 수 없어, 인가 서버 분리와 외부 검증이 막힌다 |
| 서비스 API 키를 `client_credentials` 로 바꾸기 | 호출하는 서비스가 토큰을 받아 갱신하는 단계가 늘 뿐, 서비스 범위 격리(ADR 0004)는 키로 이미 된다 |

## 검증 근거

- OAuth 2.1 은 인가 코드 흐름에 PKCE 를 요구하고 implicit · password grant 를 없앴다. 공개 클라이언트의 리프레시 토큰은 발신자 제한 또는 회전으로 재사용을 막아야 한다.
- OAuth 2.0 for Browser-Based Applications 는 BFF · 토큰 중개 백엔드 · 브라우저 클라이언트 세 방식을 두고, 브라우저 저장소의 토큰은 같은 출처에서 도는 악성 스크립트에 노출된다고 본다. 토큰 중개 백엔드는 리프레시 토큰을 브라우저 밖에 두면서 API 는 Bearer 토큰을 그대로 받는다.
- MCP 인가 명세는 인가 서버에 OAuth 2.1 과 메타데이터 발견(RFC 8414 또는 OpenID Connect Discovery)을, MCP 서버에 보호 리소스 메타데이터(RFC 9728)와 토큰 audience 검증(RFC 8707)을 요구한다. 클라이언트 등록은 Client ID Metadata Documents 를 권하고, 동적 등록은 하위 호환으로만 남긴다. 인가 서버는 리소스 서버와 함께 두어도 따로 두어도 된다.
- Spring Authorization Server 는 Spring Security 7.0 에 합쳐졌고 좌표(`groupId` · `artifactId`)는 그대로다.
- 리소스 서버가 받는 사람용 토큰은 모두 다음을 만족해야 한다 — `iss` 는 워크벤치 발급자, `aud` 는 API 정본 주소를 포함, 서명 키는 JWKS 에 있다.

## 하지 않는 것

- 홈서버의 다른 서비스(블로그 Admin · Grafana 등)를 이 로그인에 묶는 SSO
- 서비스 API 키의 OAuth 전환
- 토큰 교환(RFC 8693) · DPoP 같은 발신자 제한 토큰
- 1 · 2단계의 scope 세분화와 동의 화면(3단계에서 다룬다)

## 다시 검토할 조건

- 다른 서비스가 이 로그인으로 들어와야 할 때 — 인가 서버를 떼어 낸다(같은 호스트의 경로 라우팅).
- 인가와 API 의 배포 주기나 가용성 요구가 갈릴 때.
- 서명 키와 사용자 정보를 API 와 격리해야 할 만큼 보안 요구가 커질 때.
- MCP 명세가 Client ID Metadata Documents 를 필수로 올리거나 동적 등록을 없앨 때 — 2단계의 등록 방식을 고친다.

## 지표와 로그

- 토큰 발급과 실패를 grant 종류(`authorization_code` · `refresh_token`)와 클라이언트별로 센다. 갱신 실패가 늘면 쿠키 · 회전 설정이 어긋난 신호다.
- 발급 로그에는 `sub` · `client_id` · `jti` · 만료 시각만 남기고 토큰 값은 남기지 않는다. 지금 접근 토큰 발급 로그와 같은 규칙이다.
- 리소스 서버의 검증 실패는 사유(서명 · 발급자 · audience · 만료)별로 센다. 전환 기간에는 기존 방식 토큰의 검증 수도 세어, 0 이 되면 기존 검증을 지운다.

## 참고 사례

- [OAuth 2.1](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1) — 인가 코드 + PKCE, 리프레시 토큰 회전
- [OAuth 2.0 for Browser-Based Applications](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps) — BFF · 토큰 중개 백엔드 · 브라우저 클라이언트
- [MCP 인가 명세](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization) — 인가 서버 · 보호 리소스 메타데이터 · Client ID Metadata Documents
- [Spring Authorization Server moving to Spring Security 7.0](https://spring.io/blog/2025/09/11/spring-authorization-server-moving-to-spring-security-7-0)
- [GitLab 을 OAuth 2.0 인증 공급자로 쓰기](https://docs.gitlab.com/integration/oauth_provider) — GitLab 은 인가 서버(Doorkeeper)를 본체 애플리케이션 안에 두고, 같은 인스턴스의 `/oauth/authorize` · `/oauth/token` 으로 외부 애플리케이션 로그인과 자기 API 토큰을 함께 발급한다.
