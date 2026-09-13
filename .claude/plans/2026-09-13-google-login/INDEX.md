# Plan: Google 로그인 + 자체 토큰 발급 + UserIdentity 분리
Created: 2026-09-13
Status: active

## 결정 사항

| # | 결정 | 이유 |
|---|---|---|
| D1 | 자체 JWT 발급 (Google 검증 후 내 토큰) | claim·수명·audience 를 명시적으로 통제. Google ID token 을 API Bearer 로 쓰지 않는다 |
| D2 | `OwnerContext.userId` = 내부 `Long`, JWT `sub` = `uuid` | FK·인덱스는 내부 PK, 외부 노출은 uuid |
| D3 | `User` / `UserIdentity` 1:N 분리 | 계정 연결(한 사람이 Google+GitHub)을 실제로 할 것이므로 |
| D4 | 식별 기준은 (issuer, subject), `provider` 컬럼 없음 | 파생 가능한 값을 중복 저장하면 불일치 상태가 생긴다 |
| D5 | HS256 대칭키 | 검증자가 내 프로세스 하나뿐. RS256+JWKS 는 검증자가 분리될 때 |
| D6 | `User` 에 `@OneToMany` 두지 않음 (`UserIdentity` 단방향 `@ManyToOne` 만) | 읽는 곳이 한 곳인데 모든 조회가 컬렉션을 끌고 다녔다. 지켜줄 것처럼 보이던 불변식도 `@Version` 없이는 강제되지 않는다 |
| D7 | 로그인 조회는 `join fetch` 로 명시 | 프록시 해석으로 쿼리가 조용히 한 번 더 나가던 것을 JOIN 1개로 |

## Progress
- [x] 1. `User` / `UserIdentity` 모델 분리 + `UserRegistry` 공개 경계 + 테스트 12개
- [x] 2. Google OIDC 로그인 + JIT provisioning + 이메일 allowlist
- [x] 3. 자체 access token 발급 + `/api/**` Bearer 체인 + `/api/me` + Auth Playground
- [ ] 4. refresh token (rotation + reuse detection) + 로그아웃
- [ ] 5. GitHub provider — OIDC 아님, issuer 상수 지정 필요
- [ ] 6. 계정 연결 — state 에 현재 user 바인딩 (아래 함정 참고)
- [ ] 7. `GenerationJob` + owner scope 조회

## Resume Point

4단계부터. `RefreshToken` 엔티티를 `auth/domain` 에 추가하고,
`OAuth2LoginSuccessHandler` 가 access token 과 함께 refresh token 을 발급하도록 확장한다.

## 알려진 함정 (다음 단계에서 밟는다)

1. **계정 연결(6단계)**: OAuth 리다이렉트에는 `Authorization` 헤더가 실리지 않는다.
   "이미 로그인 상태" 를 콜백에서 알 수 없으므로 `OAuth2AuthorizationRequestResolver` 를
   커스터마이즈해 state 에 현재 userId 를 바인딩해야 한다.
2. **GitHub(5단계)**: `https://github.com/.well-known/openid-configuration` 는 404 다.
   OIDC 가 아니므로 `iss` 가 없고, issuer 를 상수로 정해야 한다.
   subject 는 숫자 `id` 를 쓴다 — `login` 은 변경 가능하다.
3. **refresh token(4단계)**: DB 에 저장해야 폐기가 가능하다.
   그 순간 "stateless" 의 명분 절반이 사라지는데, 이게 정상이다.
4. `DefaultUserRegistry.resolveOrRegister` 는 바깥 트랜잭션 안에서 호출하면 안 된다.
   경쟁 재시도가 rollback-only 마킹 때문에 무의미해진다.
5. **`unlink` 의 "마지막 하나 금지" 는 TOCTOU 에 열려 있다.** 동시 두 건이 모두 통과할 수 있다.
   6단계에서 해제 UI 를 붙일 때 `User` 에 `@Version` 을 추가하거나 부모 행을 비관적 락으로 잡아야 한다.
   (`@OneToMany` 가 있었어도 `@Version` 없이는 동일한 문제였다 — Aggregate 경계가 락을 대신하지 않는다.)
6. `User` 삭제 경로를 만들 때 `user_identities` 를 먼저 지워야 한다.
   cascade/orphanRemoval 이 없으므로 FK 제약에 걸린다. 현재 삭제 경로는 없다.

## Files
- 코드: `src/main/kotlin/dev/joyson/aiworkbench/{user,auth,ownership}/`
- 샘플 클라이언트: `src/main/resources/static/index.html`
- 문서: `README.md` 인증 섹션, `docs/domain/domain-model.md`
