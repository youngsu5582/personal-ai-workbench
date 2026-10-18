#!/usr/bin/env bash
# 글 생성 요청을 보낸다.
#
#   ./scripts/text.sh "이 글을 세 줄로 요약해: ..."
#   ./scripts/text.sh "태그 다섯 개" 1 gpt-6.1-sol             # 모델 지정
#   INSTRUCTIONS="너는 기술 블로그 편집자다" ./scripts/text.sh "제목을 지어줘"
#   EFFORT=low ./scripts/text.sh "태그 다섯 개"                # none · low · medium · high · xhigh · max
#   SCHEMA='{"type":"object","properties":{"tags":{"type":"array","items":{"type":"string"}}},"required":["tags"],"additionalProperties":false}' \
#     ./scripts/text.sh "이 글의 태그를 골라: ..."
#   BASE_URL=http://localhost:8099 ./scripts/text.sh "안녕"
set -euo pipefail
cd "$(dirname "$0")/.."

PROMPT="${1:-워크벤치가 무엇인지 한 문장으로 소개해줘}"
TASK_COUNT="${2:-1}"
MODEL="${3:-gpt-6-luna}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

TOKEN="$(./scripts/dev-token.sh "${USER_UUID:-}")"

BODY=$(python3 - "$PROMPT" "$TASK_COUNT" "$MODEL" "${INSTRUCTIONS:-}" "${SCHEMA:-}" "${EFFORT:-}" <<'JSON'
import json, sys
prompt, count, model, instructions, schema, effort = sys.argv[1:7]

option = {"type": "text-generate", "prompt": prompt}
if instructions:
    option["instructions"] = instructions
# 주지 않으면 보내지 않는다 — 모델 기본값을 따른다.
if effort:
    option["effort"] = effort
# 스키마가 있으면 JSON 객체로 답을 받는다. 키 순서가 답의 키 순서라 받은 순서 그대로 싣는다 —
# json.loads 는 순서를 지킨다.
if schema:
    option["output"] = {"type": "json-schema", "schema": json.loads(schema)}

print(json.dumps({"option": option, "model": model, "taskCount": int(count)}, ensure_ascii=False))
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
