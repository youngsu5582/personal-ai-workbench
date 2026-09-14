#!/usr/bin/env bash
# T2I 생성 요청을 보낸다.
#
#   ./scripts/t2i.sh "고양이가 창밖을 본다"
#   ./scripts/t2i.sh "고양이" 4                  # 4장
#   BASE_URL=http://localhost:8099 ./scripts/t2i.sh "고양이"
set -euo pipefail
cd "$(dirname "$0")/.."

PROMPT="${1:-고양이가 창밖을 보는 수채화}"
TASK_COUNT="${2:-1}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

TOKEN="$(./scripts/dev-token.sh "${USER_UUID:-}")"

BODY=$(python3 - "$PROMPT" "$TASK_COUNT" <<'PY'
import json, sys
prompt, count = sys.argv[1], int(sys.argv[2])
# option 은 다형성 JSON 이다. type 이 판별자이고, 값은 GenerationJobType 의 @JsonValue 와 같아야 한다.
print(json.dumps({
    "option": {"type": "text-to-image", "prompt": prompt},
    "taskCount": count,
}, ensure_ascii=False))
PY
)

echo "  POST $BASE_URL/api/jobs"
echo "  $BODY"
echo

RESPONSE=$(curl -sS -w '\n%{http_code}' \
  -X POST "$BASE_URL/api/jobs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$BODY")

STATUS=$(printf '%s' "$RESPONSE" | tail -1)
PAYLOAD=$(printf '%s' "$RESPONSE" | sed '$d')

echo "  HTTP $STATUS"
printf '%s' "$PAYLOAD" | python3 -m json.tool 2>/dev/null | sed 's/^/  /' || echo "  $PAYLOAD"
