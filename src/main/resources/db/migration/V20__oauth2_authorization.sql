-- 인가 서버가 발급한 인가(인가 코드 · 리프레시 토큰)를 담는다. 인가 하나가 한 행이다.
--
-- 코드를 토큰으로 바꿀 때와 갱신할 때 다시 대조해야 하는 값만 둔다.
--   - 토큰은 원문이 아니라 SHA-256 해시를 둔다. 찾을 때는 받은 값을 해시해 이 열과 맞춘다.
--     DB 가 유출돼도 토큰으로 쓸 수 없다.
--   - 접근 토큰은 두지 않는다. API 가 서명만으로 검증하고, 조회 · 폐기 엔드포인트는 쓰지 않는다.
--   - 인가 요청은 JSON 한 덩어리가 아니라 대조에 쓰는 값만 열로 둔다. 로그인한 사람은 이름(사용자 UUID)뿐이다.
--   - `version` 으로 낙관적 잠금을 건다. 코드를 읽고 → 검사하고 → 저장하는 사이에 다른 요청이 먼저 저장했으면
--     저장이 0행이 되어 거절된다. 같은 코드 · 같은 리프레시 토큰이 동시에 두 번 쓰이는 것을 막는다.
--
-- `if not exists` 인 이유: 테스트는 Flyway 를 켜지 않고 이 파일을 스키마 초기화로 직접 실행한다(컨텍스트마다 다시 실행된다).
create table if not exists oauth2_authorization (
    id                             varchar(100)  not null,
    version                        bigint        not null default 0,
    registered_client_id           varchar(100)  not null,
    principal_name                 varchar(200)  not null,

    -- 인가 요청. 인가 코드를 토큰으로 바꿀 때 redirect_uri 와 PKCE 를 대조한다.
    redirect_uri                   varchar(2000) not null,
    state                          varchar(500),
    scopes                         varchar(1000) not null,
    code_challenge                 varchar(200)  not null,
    code_challenge_method          varchar(20)   not null,

    authorization_code_hash        varchar(64),
    authorization_code_issued_at   timestamptz,
    authorization_code_expires_at  timestamptz,
    authorization_code_invalidated boolean       not null default false,

    refresh_token_hash             varchar(64),
    refresh_token_issued_at        timestamptz,
    refresh_token_expires_at       timestamptz,
    refresh_token_invalidated      boolean       not null default false,

    primary key (id),
    -- 해시로 찾으므로 유일 제약이 곧 인덱스다. null 은 여러 행이 가질 수 있다.
    unique (authorization_code_hash),
    unique (refresh_token_hash)
);
