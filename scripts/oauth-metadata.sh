#!/usr/bin/env bash
# 인가 서버가 밖으로 여는 것을 본다 — 메타데이터와 공개키(JWKS).
#
#   ./scripts/oauth-metadata.sh
#   BASE_URL=http://localhost:8099 ./scripts/oauth-metadata.sh
#
# 메타데이터(RFC 8414)는 엔드포인트 주소와 지원하는 기능의 목록이다.
# JWKS 경로는 메타데이터의 jwks_uri 에서 읽는다 — 경로를 여기에 적으면 서버 설정과 어긋날 수 있다.
# jwks_uri 는 발급자 주소로 만들어진다. 로컬의 발급자는 실제로 열리는 주소가 아니라서,
# 경로만 떼어 BASE_URL 에 붙여 부른다.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
META_URL="$BASE_URL/.well-known/oauth-authorization-server"

echo "  GET $META_URL"
META="$(curl -sS -f "$META_URL")" || {
  echo "  메타데이터를 받지 못했다" >&2
  exit 1
}
printf '%s' "$META" | python3 -m json.tool | sed 's/^/  /'

JWKS_URI="$(printf '%s' "$META" | python3 -c 'import json, sys; print(json.load(sys.stdin)["jwks_uri"])')"
JWKS_PATH="$(python3 -c 'import sys; from urllib.parse import urlparse; print(urlparse(sys.argv[1]).path)' "$JWKS_URI")"

echo
echo "  jwks_uri = $JWKS_URI"
echo "  GET $BASE_URL$JWKS_PATH"
curl -sS -f "$BASE_URL$JWKS_PATH" | python3 -m json.tool | sed 's/^/  /'
