#!/usr/bin/env bash
# 리프레시 토큰의 회전을 손으로 따라가 본다.
#
#   ./scripts/oauth-refresh.sh '<code>'          # 1) 인가 코드를 토큰으로 바꾸고, 받은 리프레시 토큰을 var/oauth-refresh-token 에 둔다
#                                                #    oauth-code.sh '<code>' 로 이미 바꿨다면 이 단계는 건너뛴다 — 같은 code 를 두 번 쓸 수 없다
#   ./scripts/oauth-refresh.sh                   # 2) 저장한 리프레시 토큰으로 갱신한다 (돌릴 때마다 새 토큰으로 바뀐다)
#   ./scripts/oauth-refresh.sh --old             # 3) 직전에 쓴 옛 리프레시 토큰을 다시 내본다
#
# 먼저 ./scripts/oauth-code.sh 로 인가 주소를 만들고, 브라우저에서 로그인해 돌아온 주소의 code 를 1) 에 넣는다.
# 인가 코드는 한 번만 쓸 수 있다.
#
# 3) 이 단계의 요점이다 — 옛 토큰은 거절되는데, 그 뒤에도 새 토큰이 살아 있는지 2) 로 확인한다.
# 살아 있다면 서버가 "옛 토큰이 다시 왔다" 는 사실을 기억하지 못하는 것이다.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
REDIRECT_URI="${REDIRECT_URI:-$BASE_URL/auth/session/callback}"
CLIENT_ID="workbench-console"
VERIFIER_FILE="var/oauth-code-verifier"
CURRENT_FILE="var/oauth-refresh-token"
PREVIOUS_FILE="var/oauth-refresh-token.previous"

SECRET="${AUTH_CONSOLE_CLIENT_SECRET:-$(grep '^AUTH_CONSOLE_CLIENT_SECRET=' .env 2>/dev/null | cut -d= -f2- || true)}"
[ -n "$SECRET" ] || { echo "AUTH_CONSOLE_CLIENT_SECRET 이 없다" >&2; exit 2; }

# 아이디와 비밀값은 Basic 헤더에 넣기 전에 form 인코딩해야 한다(RFC 6749 §2.3.1).
# 서버가 꺼낸 값을 디코드하므로, `+` · `%` 가 든 비밀값을 그대로 보내면 올바른 토큰으로도 401 이다.
urlencode() { python3 -c 'import sys, urllib.parse; print(urllib.parse.quote_plus(sys.argv[1]), end="")' "$1"; }
BASIC_USER="$(urlencode "$CLIENT_ID"):$(urlencode "$SECRET")"
mkdir -p var

token_request() {
  # $@ : curl 에 넘길 -d 인자들
  curl -sS -w '\n%{http_code}' -u "$BASIC_USER" "$@" "$BASE_URL/oauth2/token"
}

show() {
  local response="$1" status payload
  status="$(printf '%s' "$response" | tail -1)"
  payload="$(printf '%s' "$response" | sed '$d')"
  echo "  HTTP $status"
  printf '%s' "$payload" | python3 -m json.tool | sed 's/^/  /'
  case "$status" in 2*) ;; *) return 1 ;; esac
  REFRESH_TOKEN="$(printf '%s' "$payload" | python3 -c 'import json,sys; print(json.load(sys.stdin)["refresh_token"])')"
}

if [ $# -eq 1 ] && [ "$1" != "--old" ]; then
  [ -f "$VERIFIER_FILE" ] || { echo "먼저 ./scripts/oauth-code.sh 를 인자 없이 돌려 인가 주소를 만든다" >&2; exit 2; }
  echo "  1) 인가 코드 → 토큰"
  RESPONSE="$(token_request -d grant_type=authorization_code --data-urlencode "code=$1" \
    --data-urlencode "redirect_uri=$REDIRECT_URI" --data-urlencode "code_verifier=$(cat "$VERIFIER_FILE")")"
  show "$RESPONSE"
  rm -f "$PREVIOUS_FILE"
  printf '%s' "$REFRESH_TOKEN" > "$CURRENT_FILE"
  exit 0
fi

[ -f "$CURRENT_FILE" ] || { echo "먼저 인가 코드로 1) 을 돌린다" >&2; exit 2; }

if [ "${1:-}" = "--old" ]; then
  [ -f "$PREVIOUS_FILE" ] || { echo "옛 토큰이 없다. 2) 를 한 번 돌린 뒤에 쓴다" >&2; exit 2; }
  echo "  3) 옛 리프레시 토큰을 다시 낸다"
  show "$(token_request -d grant_type=refresh_token --data-urlencode "refresh_token=$(cat "$PREVIOUS_FILE")")" || true
  exit 0
fi

echo "  2) 리프레시 토큰으로 갱신"
cp "$CURRENT_FILE" "$PREVIOUS_FILE"
show "$(token_request -d grant_type=refresh_token --data-urlencode "refresh_token=$(cat "$CURRENT_FILE")")"
printf '%s' "$REFRESH_TOKEN" > "$CURRENT_FILE"
echo
echo "  새 리프레시 토큰은 이전 것과 $([ "$(cat "$CURRENT_FILE")" = "$(cat "$PREVIOUS_FILE")" ] && echo 같다 || echo 다르다)"
