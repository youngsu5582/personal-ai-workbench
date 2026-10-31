#!/usr/bin/env bash
# 판정 요청을 보낸다. 질문 세 종류(predicate · choice · score)를 한 요청에 실어 각 답의 모양을 한 번에 본다.
#
#   ./scripts/decision.sh "다음 달부터 구독을 해지하고 싶어요"
#   ./scripts/decision.sh "사진을 보고 파손 여부를 판단해줘" <sha256>             # 생성 결과를 이미지 근거로
#   SOURCE_TYPE=uploaded ./scripts/decision.sh "파손 여부" <sha256>              # 올린 파일을 이미지 근거로
#   ./scripts/decision.sh "환불하고 싶어요" "" gpt-6-luna                         # 모델 지정
#   BASE_URL=http://localhost:8099 ./scripts/decision.sh "환불하고 싶어요"
#
# 판정은 한 호출이 질문을 모두 답하므로 taskCount 는 늘 1 이다.
set -euo pipefail
cd "$(dirname "$0")/.."

TEXT="${1:-다음 달부터 구독을 해지하고 싶어요. 오늘 안에 처리해 주세요.}"
SOURCE="${2:-}"
MODEL="${3:-gpt-6-luna}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

TOKEN="$(./scripts/dev-token.sh "${USER_UUID:-}")"

BODY=$(python3 - "$TEXT" "$SOURCE" "${SOURCE_TYPE:-generated}" "$MODEL" <<'JSON'
import json, sys
text, source, source_type, model = sys.argv[1:5]

evidence = [{"type": "text", "text": text}]
if source:
    evidence.append({"type": "image", "source": {"type": source_type, "sha256": source}})

# 예시 질문이다. 쓰는 쪽은 자기 판정 기준과 선택지를 직접 적는다.
questions = [
    {"type": "predicate", "name": "needs_review", "instructions": "사람의 추가 확인이나 처리가 필요한 요청인가?"},
    {"type": "choice", "name": "intent", "instructions": "입력의 주된 의도를 가장 가까운 하나로 고른다.",
     "choices": [
         {"value": "cancel", "description": "환불 · 취소 · 해지를 원한다."},
         {"value": "question", "description": "정보나 도움을 묻는다."},
         {"value": "other", "description": "둘 중 어디에도 해당하지 않는다."},
     ]},
    {"type": "score", "name": "urgency", "instructions": "입력에서 드러난 처리 긴급도를 평가한다.",
     "levels": [
         {"label": "low", "description": "바로 처리할 이유가 없다."},
         {"label": "high", "description": "빨리 확인하거나 처리해야 한다."},
     ]},
]

print(json.dumps({"option": {"type": "decision", "input": evidence, "questions": questions},
                  "model": model, "taskCount": 1}, ensure_ascii=False))
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
