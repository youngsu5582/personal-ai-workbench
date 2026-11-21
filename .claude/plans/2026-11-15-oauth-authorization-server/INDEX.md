# Plan: OAuth 2.1 인가 서버 1단계 — 콘솔
Created: 2026-11-15
Status: active

결정은 `docs/adr/0005-oauth-authorization-server.md` 에 있다(PR #102, 2026-11-15 머지). 이 문서는 그 1단계를 어떤 순서로 만드는지만 적는다.

## 정해진 것

- 대상은 1단계(콘솔)뿐이다. 2단계(CLI · MCP 클라이언트)는 1단계가 배포된 뒤 따로 시작한다.
- **PR 하나가 관심사 하나다.** PR 마다 OAuth 개념 하나를 만들고, 머지 전에 뜬 서버를 직접 두드려 동작을 본다.
- PR 1 ~ 5 는 기존 로그인(handoff 쿠키 + HS256)을 건드리지 않는다. 전환은 PR 6 에서만 일어난다 — ADR 이 두 방식을 함께 받는 기간을 두지 않기로 했다.
- PR 3 이 ADR 의 검증 지점이다. "Spring Authorization Server 는 회전한 옛 토큰을 기억하지 않는다" 는 전제를 여기서 직접 본다. 다르면 ADR 을 고치고 PR 4 의 설계를 바꾼다.
- PR 마다 확인 스크립트를 `scripts/` 에 남기고, PR 본문에 "내 말로 정리" 를 쓴다 — 무엇을 봤고 예상과 무엇이 달랐는지. 블로그 글의 "직접 확인" 절 재료가 된다.
- 스택 중간에는 배포하지 않는다. 스택을 다 쌓은 뒤 순서대로 머지한다 (덜 만든 인가 엔드포인트가 공개되지 않게. 켜고 끄는 설정 키는 만들지 않는다).
- 역할 — 구현은 AI 가 하고, 영수님은 PR 마다 뜬 서버를 직접 두드려 동작을 확인한다 (2026-11-16 변경).
- `authserver` 는 별도 Gradle 모듈(`module-authserver/`)이다. Gradle 모듈의 폴더에는 `module-` 를 붙여, 루트의 다른 폴더(`docs` · `deploy` · `frontend`)와 구분한다. 패키지와 설정 접두사는 `authserver` 그대로다. 인가 서버 라이브러리는 그 모듈의 `implementation` 이라 앱의 다른 모듈에는 보이지 않는다.
  - `authserver` 는 앱의 다른 모듈을 import 하지 않는다. 앱의 것이 필요하면 `authserver` 가 인터페이스를 선언하고 앱이 구현한다.
  - 테스트는 앱 모듈(`src/test`)에 둔다. 인가 서버의 동작은 앱 전체를 띄워야 드러나고, `.claude/scripts/test.sh` 와 CI 가 앱 모듈의 결과만 모은다.
  - `.claude/conventions.json` 의 `sourceRoot` 는 `src/main/kotlin` 하나다. AI 가 파일을 쓰는 순간의 관례 검사는 `module-authserver/` 아래에 걸리지 않는다.
  - Spring Modulith 의 모듈 검사는 `authserver` 를 여전히 모듈로 본다(일회용 테스트로 확인).
  - 설정 접두사는 `workbench.authserver` 가 아니라 최상위 `authserver` 다. 모듈이 앱에서 떨어져 나갈 때 설정 블록이 그대로 따라가게 한다. 환경변수 이름(`AUTH_…`)은 그대로 둔다.

## 스택

| PR | 브랜치 | 관심사 | 만드는 것 | 직접 확인하는 방법 |
|---|---|---|---|---|
| 1 | `feat/authserver-metadata` | 인가 서버가 밖으로 여는 것 · 비대칭 서명 | `authserver` 모듈 뼈대, RSA 서명 키, 메타데이터, JWKS | `./scripts/oauth-metadata.sh` — 엔드포인트 목록과 공개키가 보인다 |
| 2 | `feat/authserver-code-pkce` | 인가 코드 + PKCE · 연합 로그인 | 콘솔용 클라이언트 등록, Google 로그인을 인가 서버 뒤에 연결 | 브라우저로 인가 요청 → 받은 코드를 토큰과 교환. `code_verifier` 를 틀리게 보내 거절되는 것도 본다. 토큰을 디코드해 `iss` · `aud` · `sub` 를 읽는다 |
| 3 | `feat/authserver-refresh-rotation` | 리프레시 토큰과 회전 (라이브러리 기본 동작) | 회전 설정, 인가 · 토큰 테이블 | 리프레시를 두 번 한다. 옛 토큰을 다시 보내면 무슨 일이 일어나는지 본다 |
| 4 | `feat/authserver-reuse-detection` | 재사용 감지 · 겹침 시간 | 물러난 토큰 테이블, 판정 로직 | ADR 의 판정 그림 네 갈래를 각각 재현한다 — 회전 · 거절 · 다시 주기 · 인가 폐기 |
| 5 | `feat/console-token-mediation` | 토큰 중개 백엔드 · HttpOnly 쿠키 | 콘솔 백엔드의 코드 교환 · 갱신 · 로그아웃 엔드포인트 | 개발자 도구에서 쿠키를 보고, 콘솔 창의 `document.cookie` 로는 안 보이는 것을 확인한다 |
| 6 | `feat/oauth-cutover` | 리소스 서버의 검증 · 전환 | API 를 JWKS 검증으로, 프런트를 새 흐름으로, handoff · HS256 삭제 | 새로고침해도 로그인이 유지된다. 옛 HS256 토큰은 401 을 받는다 |

## 아직 정하지 않은 것

- 완료 기준 한 문장 — AI 제안: "콘솔을 새로고침해도 로그인이 유지되고, handoff · HS256 코드가 저장소에서 지워졌다"
- 숫자 — 접근 · 리프레시 토큰 수명, 겹침 시간 (PR 3 전에)
- 콘솔 세션 엔드포인트의 경로와 응답 — 갱신 · 로그아웃 (PR 5 전에)

## 구현하며 확인할 것 (PR #102 본문의 미검증 항목)

- Spring 의 JDBC 저장소가 토큰 값을 다시 꺼낼 수 있게 두는지 — 겹침 시간에 같은 쌍을 돌려주는 전제다 (PR 3 · 4)
- 콘솔 백엔드가 기동 중 자기 자신에게 메타데이터를 묻지 않도록 엔드포인트를 직접 적어야 하는지 (PR 5)
- ~~인가 서버의 보안 설정이 기존 `SecurityConfig` 와 충돌 없이 붙는지 (PR 1)~~ — 붙는다. 인가 서버 체인은 자기 엔드포인트만 받고, Google 로그인 시작 경로(`/oauth2/authorization/google`)는 그대로 로그인 체인으로 간다 (`AuthorizationServerMetadataTest`)

## PR 1 에서 정한 것

- 발급자는 Boot 의 표준 키 `spring.security.oauth2.authorizationserver.issuer` 가 `${workbench.auth.issuer}` 를 가리키게 한다. 새 설정 키와 `auth` 모듈 의존(코드)을 만들지 않으려는 것이다. 환경변수 `AUTH_ISSUER` 를 직접 가리키면 안 된다 — local 프로필은 환경변수 없이 `workbench.auth.issuer` 를 직접 정해, 자리표시자가 풀리지 않은 채 기동이 실패한다.
- 테스트 설정은 `application.yaml` 을 읽지 않는다. 그래서 `application.yaml` 의 자리표시자가 프로필에서 풀리는지는 테스트가 잡지 못하고, 직접 띄워야 드러난다.
- 서명 키는 RSA 개인키(PEM 파일) 하나이고 서명 방식은 RS256 이다(`authserver.signing-key` 에 파일 위치를 적는다). Spring Security 가 PEM 을 키로 바꿔 주는 것이 RSA 뿐이라, 이 길로 가면 키를 읽는 코드를 직접 두지 않는다. 처음에는 ES256 + JWK Set 한 줄이었다가 바꿨다 — 이유는 ADR 0005 의 대안 표에 있다.
  - 로컬은 `var/keys/authserver-signing-key.pem` 을 본다(로컬 프로필). 만드는 명령은 `docs/development.md` 에 있다. 워크트리마다 `var/` 가 따로라 키 파일도 따로 만든다.
  - 테스트는 `TestSigningKey` 가 실행할 때마다 키를 만들어 넣는다. 저장소에 개인키를 두지 않는다.
- 등록된 클라이언트는 아직 없다(`AuthorizationServerConfig` 의 빈 저장소). PR 2 에서 콘솔용 클라이언트로 바꾼다.
- 메타데이터는 내주기로 한 것만 알린다. 라이브러리 기본값은 쓰지 않는 grant(`client_credentials` · 토큰 교환)와 인증 방식 다섯 가지, 토큰 조회, DPoP · mTLS 까지 적는다. 내주는 것은 `AuthorizationServerConfig` 의 목록 두 개가 정하고, grant 를 더하는 PR 이 그 목록에도 더한다(PR 3 의 `refresh_token`).
- 토큰 조회 엔드포인트는 접근 규칙으로 막는다. 라이브러리에 끄는 설정이 없다. API 는 JWKS 로 직접 검증한다.
- 라이브러리가 기본으로 켜는 엔드포인트는 여섯이다 — 메타데이터 · JWKS · 인가 · 토큰 · 폐기 · 토큰 조회. 인가 요청 밀어 넣기(PAR) · 기기 인가 · 클라이언트 등록 · OpenID Connect 는 따로 설정해야 켜진다.
- DPoP 는 메타데이터에서만 뺐다. 기능 자체를 끄는 방법은 찾지 못했다. 2단계에서 CLI · MCP 클라이언트를 받기 전에 정리한다.

## PR 2 에서 정한 것

- 콘솔 클라이언트의 `client_id` 는 `workbench-console`, 인증은 `client_secret_basic`, grant 는 `authorization_code` 뿐이다(리프레시는 PR 3). 기밀 클라이언트지만 PKCE 를 요구한다.
- 콜백 주소는 `{발급자}/auth/session/callback` 이다. 로컬은 `http://localhost:8080/auth/session/callback`. 받는 엔드포인트는 PR 5 가 만든다 — 그때까지는 404 다.
- 접근 토큰의 `sub` 는 워크벤치 사용자 UUID 다. `WorkbenchOidcUser` 의 이름을 UUID 로 바꿔, 인가 서버가 `auth` 모듈의 타입을 모른 채 그 이름을 쓴다.
- `aud` 와 수명은 `workbench.auth.audience` · `workbench.auth.access-token-ttl` 을 가리킨다. 어느 쪽이 발급해도 토큰의 모양이 같다.
- 서명 방식과 `kid` 는 따로 정하지 않는다. 라이브러리의 기본이 RS256 이고, 인코더가 서명 키의 `kid` 를 토큰 머리말에 넣는다.
- 로그인 성공 처리(`OAuth2LoginSuccessHandler`)는 인가 엔드포인트로 가다가 온 로그인이면 그 요청으로 돌려보내고 세션을 남긴다. 그 밖에는 지금처럼 handoff 로 간다.
  - `auth` 모듈은 인가 서버 라이브러리를 볼 수 없다. 인가 엔드포인트의 경로는 `authserver` 가 공개한 `AuthorizationServerPaths` 로 안다.
  - Google 로그인(`OidcUserService` · 성공 처리)은 인가 서버의 로그인 단계라 `authserver` 쪽이 제자리다. handoff 가 없어지는 PR 6 에서 옮긴다. 그때 `user` 모듈의 포트는 `authserver` 가 선언한 인터페이스를 앱이 구현하는 방향으로 뒤집는다.
- 콘솔 백엔드(PR 5)는 인가 서버의 클라이언트라 `authserver` 에도 `auth` 에도 넣지 않는다. 새 모듈로 만든다(이름 미정 — 제안 `console`).
- 인가 서버의 발급 기록은 메모리에 있다(라이브러리 기본). 재기동하면 사라진다. PR 3 에서 DB 로 옮긴다.

## PR 2 에서 드러난 것

- 인가 서버는 GET 인가 요청의 파라미터를 쿼리 문자열에서만 읽는다. MockMvc 의 `param()` 은 쿼리 문자열을 만들지 않아 `response_type` 이 없다고 거절된다 — 테스트는 주소에 직접 싣는다.
- `redirect_uri` 의 `localhost` 는 거절되지 않는다(Spring Security 7.1.1, 일회용 테스트로 확인).
- 기존 토큰의 `name` 클레임(표시 이름)은 인가 서버가 발급한 토큰에 없다. 전환(PR 6) 전에 이 클레임을 읽는 곳이 있는지 본다.
- 인가 흐름에서는 로그인 세션이 남는다. 세션이 살아 있는 동안에는 Google 을 다시 거치지 않고 인가 코드가 나온다.

## PR 2 에서 주의했던 것 (해결)

- `auth` 모듈의 `JwtEncoder` 빈(HS256)이 이미 있다. 인가 서버는 빈으로 등록된 `JwtEncoder` 를 찾아 토큰 서명에 쓰므로, 그대로 두면 접근 토큰이 HS256 으로 나간다. 토큰 인코더를 인가 서버가 직접 만들어야 한다.
- 로그인하지 않은 사람이 인가 엔드포인트에 오면 Google 로그인으로 보내는 진입점이 인가 서버 체인에 없다.
- 등록된 클라이언트가 없는 PR 1 의 메타데이터는 `authorization_code` 와 `client_secret_basic` 을 알린다. PR 2 가 등록하는 콘솔 클라이언트가 이 둘만 쓰게 한다.
- 로그인하지 않은 요청을 Google 로그인으로 보내는 것은 인가 엔드포인트뿐이다. 나머지는 401 로 답한다.

## 배포 전에 할 것

- 서명 키 파일을 홈서버 Secret 으로 만들어 파드에 파일로 붙이고, `AUTH_SIGNING_KEY` 에 그 위치를 적는다. 지금 배포 설정은 비밀을 환경변수로만 넣으므로 `deploy/` 에 파일을 붙이는 설정을 더해야 한다(인프라 변경 — 사용자 확인 뒤). 없으면 기동이 실패한다.
- 홈서버 Secret(`runtime.env`)에 `AUTH_CONSOLE_CLIENT_SECRET` 을 넣는다. 없으면 기동이 실패한다.

## Progress

- [x] ADR 0005 머지 — PR #102, merge `e372c59` (2026-11-15)
- [x] 계획 · PR 1 워크트리 (2026-11-15)
- [x] PR 1 준비 — 의존성 한 줄, 확인 스크립트 `scripts/oauth-metadata.sh`
- [x] PR 1 구현 — `authserver` 모듈 뼈대 · RSA 서명 키 · 메타데이터 · JWKS
- [x] PR 1 직접 확인 — 서버를 띄워 `./scripts/oauth-metadata.sh` 로 메타데이터와 공개키를 봤다 (2026-11-16). `response_types_supported` 는 `code` 뿐이고 `code_challenge_methods_supported` 는 `S256` 뿐이다. JWKS 에 `d` 는 없다
- [x] PR 1 머지 — #106, merge `b184ca0` (2026-11-21). Codex P1 한 건은 "plan 의 진행 목록은 세션 메모" 라는 이유로 반영하지 않고 답글을 남긴 뒤 resolve 했다
- [x] PR 2 구현 — 콘솔 클라이언트 등록 · 접근 토큰 발급 · Google 로그인 뒤 인가 요청으로 복귀. 전체 테스트 통과 (2026-11-21)
- [ ] PR 2 직접 확인 — `.env` 에 `AUTH_CONSOLE_CLIENT_SECRET` 을 넣고 서버를 다시 띄워 `./scripts/oauth-code.sh`
- [ ] PR 3 ~ 6
- [ ] `docs/roadmap.md` 의 "OAuth 인가 서버 … 구현은 아직" 을 PR 6(전환)에서 고친다 — 사용자가 보는 동작이 바뀌는 것은 그때다

## Resume Point

PR 1 은 머지됐다(#106). PR 2 는 워크트리 `authserver-code-pkce` (브랜치 `feat/authserver-code-pkce`, base `main`)에서 구현이 끝났고 draft PR 로 열려 있다.
영수님이 직접 확인할 차례다 — `.env` 에 `AUTH_CONSOLE_CLIENT_SECRET=$(openssl rand -base64 48)` 을 넣고 서버를 다시 띄운 뒤 `./scripts/oauth-code.sh`.
확인한 뒤 PR 본문의 "내 말로 정리" 를 쓰고 ready 로 바꾼다(그때 Codex 리뷰가 돈다). PR 3 전에 숫자(리프레시 토큰 수명 · 겹침 시간)를 정해야 한다.

## Files

- INDEX.md — 이 문서
