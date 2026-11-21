#!/usr/bin/env bash
# 인가 코드 + PKCE 흐름을 손으로 따라가 본다.
#
#   ./scripts/oauth-code.sh                            # 1) 브라우저로 열 인가 주소를 찍는다
#   ./scripts/oauth-code.sh '<code>'                   # 2) 돌아온 주소의 code 를 토큰으로 바꾸고 토큰 안을 본다
#   ./scripts/oauth-code.sh '<code>' --wrong-verifier  #    code_verifier 가 다르면 거절되는 것을 본다
#
# 1) 이 만든 code_verifier 는 var/oauth-code-verifier 에 둔다. 2) 가 그것을 읽는다.
# 인가 요청에는 그 해시(code_challenge)만 실린다 — 원문은 토큰을 바꿀 때 처음 나간다.
#
# 콜백 주소에는 아직 받는 쪽이 없다. 브라우저에 404 가 떠도 주소창에 code 가 있으면 된다.
# 인가 코드는 한 번만 쓸 수 있다. 같은 code 로 2) 를 두 번 돌리면 두 번째는 거절된다.
# code 는 `code=` 와 `&state` 사이의 값만, 작은따옴표로 감싸서 넣는다. 셸은 `&` 를 "뒤로 보내라" 로 읽는다.
#
# 응답에 리프레시 토큰이 있으면 var/oauth-refresh-token 에 저장한다. 이어서 ./scripts/oauth-refresh.sh 로 갱신한다.
# 같은 code 로 oauth-refresh.sh <code> 를 또 돌리지 않는다 — 이미 썼다.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
REDIRECT_URI="${REDIRECT_URI:-$BASE_URL/auth/session/callback}"
CLIENT_ID="workbench-console"
VERIFIER_FILE="var/oauth-code-verifier"

if [ $# -eq 0 ]; then
  mkdir -p var
  VERIFIER="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
  printf '%s' "$VERIFIER" > "$VERIFIER_FILE"
  CHALLENGE="$(python3 -c 'import base64, hashlib, sys; print(base64.urlsafe_b64encode(hashlib.sha256(sys.argv[1].encode()).digest()).rstrip(b"=").decode())' "$VERIFIER")"
  ENCODED_REDIRECT="$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$REDIRECT_URI")"

  echo "  code_verifier  (나만 안다)        : $VERIFIER"
  echo "  code_challenge (인가 요청에 실린다) : $CHALLENGE"
  echo
  echo "  브라우저로 연다:"
  echo "  $BASE_URL/oauth2/authorize?response_type=code&client_id=$CLIENT_ID&redirect_uri=$ENCODED_REDIRECT&state=hello&code_challenge=$CHALLENGE&code_challenge_method=S256"
  echo
  echo "  로그인 뒤 주소창이 $REDIRECT_URI?code=...&state=hello 가 되면,"
  echo "  code= 와 &state 사이의 값만 작은따옴표로 감싸서:"
  echo "  ./scripts/oauth-code.sh '<code>'"
  exit 0
fi

CODE="$1"
[ -f "$VERIFIER_FILE" ] || { echo "먼저 인자 없이 돌려 인가 주소를 만든다" >&2; exit 2; }
VERIFIER="$(cat "$VERIFIER_FILE")"
[ "${2:-}" = "--wrong-verifier" ] && VERIFIER="wrong-$VERIFIER"

# 콘솔 백엔드의 비밀값. 환경변수에 없으면 .env 에서 읽는다.
SECRET="${AUTH_CONSOLE_CLIENT_SECRET:-$(grep '^AUTH_CONSOLE_CLIENT_SECRET=' .env 2>/dev/null | cut -d= -f2- || true)}"
[ -n "$SECRET" ] || { echo "AUTH_CONSOLE_CLIENT_SECRET 이 없다" >&2; exit 2; }

# 아이디와 비밀값은 Basic 헤더에 넣기 전에 form 인코딩해야 한다(RFC 6749 §2.3.1).
# 서버가 꺼낸 값을 디코드하므로, `+` · `%` 가 든 비밀값을 그대로 보내면 올바른 코드로도 401 이다.
urlencode() { python3 -c 'import sys, urllib.parse; print(urllib.parse.quote_plus(sys.argv[1]), end="")' "$1"; }
BASIC_USER="$(urlencode "$CLIENT_ID"):$(urlencode "$SECRET")"

echo "  POST $BASE_URL/oauth2/token  (client_secret_basic)"
RESPONSE="$(curl -sS -w '\n%{http_code}' -u "$BASIC_USER" \
  -d grant_type=authorization_code \
  --data-urlencode "code=$CODE" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode "code_verifier=$VERIFIER" \
  "$BASE_URL/oauth2/token")"

STATUS="$(printf '%s' "$RESPONSE" | tail -1)"
PAYLOAD="$(printf '%s' "$RESPONSE" | sed '$d')"
echo "  HTTP $STATUS"
printf '%s' "$PAYLOAD" | python3 -m json.tool | sed 's/^/  /'

case "$STATUS" in
  2*) ;;
  *) exit 1 ;;
esac

# 리프레시 토큰이 나왔으면 저장한다 — oauth-refresh.sh 가 이어서 쓴다.
mkdir -p var
if printf '%s' "$PAYLOAD" | python3 -c 'import json, sys; sys.exit(0 if json.load(sys.stdin).get("refresh_token") else 1)'; then
  printf '%s' "$PAYLOAD" | python3 -c 'import json, sys; print(json.load(sys.stdin)["refresh_token"], end="")' > var/oauth-refresh-token
  rm -f var/oauth-refresh-token.previous
  SAVED_REFRESH=1
fi

echo
echo "  접근 토큰 안 — 머리말과 클레임:"
printf '%s' "$PAYLOAD" | python3 -c '
import base64, json, sys
token = json.load(sys.stdin)["access_token"]
for part in token.split(".")[:2]:
    raw = base64.urlsafe_b64decode(part + "=" * (-len(part) % 4))
    print(json.dumps(json.loads(raw), indent=4, ensure_ascii=False))
' | sed 's/^/  /'

if [ "${SAVED_REFRESH:-}" = "1" ]; then
  echo
  echo "  리프레시 토큰을 var/oauth-refresh-token 에 저장했다. 이어서:"
  echo "  ./scripts/oauth-refresh.sh"
fi
