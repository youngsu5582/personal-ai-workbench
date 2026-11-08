#!/usr/bin/env bash
# 작업 종류별로 서버에 등록된 모델 목록을 조회한다.
#
#   ./scripts/models.sh image-generate
#   ./scripts/models.sh text-generate
#   ./scripts/models.sh decision
#   BASE_URL=http://localhost:8099 ./scripts/models.sh text-generate
set -euo pipefail
cd "$(dirname "$0")/.."

TYPE="${1:?사용법: ./scripts/models.sh <image-generate|text-generate|decision>}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

case "$TYPE" in
  image-generate|text-generate|decision) ;;
  *) echo "지원하지 않는 작업 종류다: $TYPE" >&2; exit 2 ;;
esac

TOKEN="$(./scripts/dev-token.sh "${USER_UUID:-}")"

echo "  GET $BASE_URL/api/models?type=$TYPE"
RESPONSE="$(curl -sS -w '\n%{http_code}' \
  -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/models?type=$TYPE")"

STATUS="$(printf '%s' "$RESPONSE" | tail -1)"
PAYLOAD="$(printf '%s' "$RESPONSE" | sed '$d')"

echo "  HTTP $STATUS"
printf '%s' "$PAYLOAD" | python3 -m json.tool 2>/dev/null | sed 's/^/  /' || {
  printf '  %s\n' "$PAYLOAD"
  exit 1
}

case "$STATUS" in
  2*) ;;
  *) exit 1 ;;
esac
