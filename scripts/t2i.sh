#!/usr/bin/env bash
# T2I 생성 요청을 보낸다.
#
#   ./scripts/t2i.sh "고양이가 창밖을 본다"
#   ./scripts/t2i.sh "고양이" 4                  # 4장
#   ./scripts/t2i.sh "고양이" 1 gpt-image-1     # 모델 지정
#   RATIO=16:9 RESOLUTION=2k ./scripts/t2i.sh "고양이"
#   BASE_URL=http://localhost:8099 ./scripts/t2i.sh "고양이"
set -euo pipefail
cd "$(dirname "$0")/.."

PROMPT="${1:-고양이가 창밖을 보는 수채화}"
TASK_COUNT="${2:-1}"
MODEL="${3:-gpt-image-2}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
RATIO="${RATIO:-1:1}"
RESOLUTION="${RESOLUTION:-1k}"
QUALITY="${QUALITY:-auto}"


TOKEN="$(./scripts/dev-token.sh "${USER_UUID:-}")"

BODY=$(python3 - "$PROMPT" "$TASK_COUNT" "$MODEL" "$RATIO" "$RESOLUTION" "$QUALITY" <<'JSON'
import json, sys
prompt, count, model, ratio, resolution, quality = sys.argv[1:7]

# option 은 다형성 JSON 이다. type 이 판별자이고, 값은 GenerationJobType 의 @JsonValue 와 같아야 한다.
# size 도 같은 방식이다(ImageSize) — 비율과 픽셀은 대체재라 둘 중 하나만 보낸다.
print(json.dumps({
    "option": {
        "type": "text-to-image",
        "prompt": prompt,
        "size": {"type": "ratio", "ratio": ratio, "resolution": resolution},
        "quality": quality,
    },
    "model": model,
    "taskCount": int(count),
}, ensure_ascii=False))
JSON
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
