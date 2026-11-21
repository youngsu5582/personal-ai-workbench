#!/usr/bin/env bash
# 글을 소리로 읽는 요청을 보낸다.
#
#   ./scripts/audio.sh "동시성 문제는 어떻게 막으셨나요?"
#   ./scripts/audio.sh "질문" Puck                              # 목소리 지정
#   ./scripts/audio.sh "질문" Kore gemini-3.8-flash-lite-tts    # 모델 지정
#   STYLE="차분하고 또렷하게" ./scripts/audio.sh "질문"
#   BASE_URL=http://localhost:8099 ./scripts/audio.sh "안녕"
#
# 결과는 출력되는 job.sh 명령으로 보고, 파일은 ./scripts/file.sh <sha256> 로 받는다.
set -euo pipefail
cd "$(dirname "$0")/.."

TEXT="${1:-그 쿠폰 발급 로직에서 동시성 문제는 어떻게 막으셨나요?}"
VOICE="${2:-Kore}"
MODEL="${3:-gemini-3.8-flash-tts}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

TOKEN="$(./scripts/dev-token.sh "${USER_UUID:-}")"

BODY=$(python3 - "$TEXT" "$VOICE" "$MODEL" "${STYLE:-}" <<'JSON'
import json, sys
text, voice, model, style = sys.argv[1:5]

option = {"type": "audio-generate", "text": text, "voice": voice}
# 주지 않으면 보내지 않는다 — 말투 지시 없이 읽는다.
if style:
    option["style"] = style

print(json.dumps({"option": option, "model": model, "taskCount": 1}, ensure_ascii=False))
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

# 접수는 202 로 끝난다. 결과는 조회로 물어야 하므로 다음 명령을 같이 알려준다.
JOB_UUID=$(printf '%s' "$PAYLOAD" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("uuid",""))' 2>/dev/null || true)
if [ -n "${JOB_UUID:-}" ]; then
  echo
  echo "  WATCH=1 ./scripts/job.sh $JOB_UUID"
fi
