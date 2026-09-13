#!/usr/bin/env bash
# 로컬 개발용 access token 을 발급한다.
#
# 서버에 개발 전용 엔드포인트를 만들지 않는 이유:
#   토큰을 찍어내는 경로가 앱에 생기면, profile 조건 하나만 잘못 걸려도 운영에 열린다.
#   서명 키가 대칭키(HS256)라 스크립트가 같은 키로 서명하면 서버가 그대로 받아들인다.
#   서버 코드는 한 줄도 바뀌지 않는다.
#
# 사용법:
#   ./scripts/dev-token.sh                      # DB 의 첫 사용자로 발급
#   ./scripts/dev-token.sh <user-uuid>          # 특정 사용자로 발급
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f .env ] || { echo "  .env 가 없다. .env.example 을 복사해 채운다." >&2; exit 1; }
set -a; . ./.env; set +a

: "${AUTH_JWT_SECRET:?AUTH_JWT_SECRET 이 .env 에 없다}"
ISSUER="${AUTH_ISSUER:-https://workbench.local}"
AUDIENCE="${AUTH_AUDIENCE:-workbench-api}"

USER_UUID="${1:-}"
if [ -z "$USER_UUID" ]; then
  USER_UUID=$(docker exec personal-ai-workbench-postgres \
    psql -U "${POSTGRES_USER:-workbench}" -d "${POSTGRES_DB:-personal_ai_workbench}" \
    -tAc "select uuid from users order by id limit 1" 2>/dev/null | tr -d '[:space:]')
  [ -n "$USER_UUID" ] || {
    echo "  사용자가 없다. 먼저 http://localhost:8080 에서 한 번 로그인한다." >&2; exit 1
  }
fi

python3 - "$USER_UUID" "$AUTH_JWT_SECRET" "$ISSUER" "$AUDIENCE" <<'PY'
import base64, hashlib, hmac, json, sys, time, uuid

user_uuid, secret, issuer, audience = sys.argv[1:5]
now = int(time.time())

def b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()

def part(obj) -> str:
    return b64(json.dumps(obj, separators=(",", ":")).encode())

# 서버의 TokenService 가 만드는 것과 같은 claim 구성이어야 검증을 통과한다.
header = part({"alg": "HS256", "typ": "JWT"})
payload = part({
    "iss": issuer,
    "sub": user_uuid,
    "aud": [audience],          # 서버가 리스트로 발급하므로 맞춘다
    "iat": now,
    "exp": now + 900,           # 15분. 서버의 access-token-ttl 과 동일
    "jti": str(uuid.uuid4()),
    "name": "dev-script",
})
signing_input = f"{header}.{payload}".encode()
signature = b64(hmac.new(secret.encode(), signing_input, hashlib.sha256).digest())
print(f"{header}.{payload}.{signature}")
PY
