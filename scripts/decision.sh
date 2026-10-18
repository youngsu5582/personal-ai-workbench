#!/usr/bin/env bash
# 텍스트(선택적으로 이미지 파일)를 정해진 질문으로 판정한다.
#
#   ./scripts/decision.sh "다음 달부터 구독을 해지하고 싶어요"
#   ./scripts/decision.sh "사진을 보고 파손 여부를 판단해줘" <파일-uuid>
#   SOURCE_TYPE=uploaded ./scripts/decision.sh "파손 여부" <업로드-uuid>
#   BASE_URL=http://localhost:8099 ./scripts/decision.sh "환불하고 싶어요"
#
# 두 번째 인자는 생성 결과 파일(기본) 또는 업로드 파일의 UUID다. Job UUID는 입력이 아니다.
set -euo pipefail

usage() {
  echo '사용법: ./scripts/decision.sh <판정할 텍스트> [입력 이미지 파일 UUID]'
  echo '환경변수: BASE_URL, ACCESS_TOKEN, USER_UUID, SOURCE_TYPE, MODEL'
}

if [ "${1:-}" = '--help' ]; then
  usage
  exit 0
fi
if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  usage >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"

for tool in curl python3; do
  command -v "$tool" >/dev/null || { echo "$tool 이 필요하다." >&2; exit 1; }
done

# 질문은 텍스트 분류와 이미지 판정을 한 요청으로 확인할 수 있게 세 종류를 함께 보낸다.
# 서비스에서는 이 예시를 복제하기보다 제품의 판정 기준과 선택지를 명시해 쓴다.
BODY=$(python3 - "$1" "${2:-}" "${SOURCE_TYPE:-generated}" "${MODEL:-gpt-6-luna}" <<'PY'
import json, sys, uuid

text, source, source_type, model = sys.argv[1:]
if not text.strip():
    sys.exit("판정할 텍스트는 비어 있을 수 없다.")
if source_type not in ("generated", "uploaded"):
    sys.exit("SOURCE_TYPE은 generated 또는 uploaded여야 한다.")
if source:
    try:
        source = str(uuid.UUID(source))
    except ValueError:
        sys.exit("입력 이미지 파일의 UUID가 필요하다.")
if not model.strip():
    sys.exit("MODEL은 비어 있을 수 없다.")

evidence = [{"type": "text", "text": text}]
if source:
    evidence.append({"type": "image", "source": {"type": source_type, "uuid": source}})

print(json.dumps({
    "option": {
        "type": "decision",
        "input": evidence,
        "questions": [
            {
                "type": "predicate",
                "name": "needs_human_review",
                "instructions": "사람의 추가 확인이나 처리가 필요한 요청인가?",
            },
            {
                "type": "choice",
                "name": "intent",
                "instructions": "입력의 주된 의도를 가장 가까운 하나로 분류한다.",
                "choices": [
                    {"value": "refund", "description": "환불, 취소, 해지를 원한다."},
                    {"value": "question", "description": "정보나 도움을 묻는다."},
                    {"value": "other", "description": "둘 중 어디에도 해당하지 않는다."},
                ],
            },
            {
                "type": "score",
                "name": "urgency",
                "instructions": "입력에서 드러난 처리 긴급도를 평가한다.",
                "levels": [
                    {"label": "low", "description": "즉시 처리할 이유가 없다."},
                    {"label": "high", "description": "빠른 확인 또는 처리가 필요하다."},
                ],
            },
        ],
    },
    "model": model,
    # 하나의 Decision 호출이 모든 질문을 답하므로 같은 요청을 Task로 복제하지 않는다.
    "taskCount": 1,
}, ensure_ascii=False))
PY
)

TOKEN="${ACCESS_TOKEN:-}"
if [ -z "$TOKEN" ]; then
  TOKEN="$("$REPO_ROOT/scripts/dev-token.sh" "${USER_UUID:-}")"
fi

TMP_DIR="$(mktemp -d -t workbench-decision.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "  POST $BASE_URL/api/jobs"
printf '  %s\n\n' "$BODY"
STATUS=$(curl --silent --show-error --connect-timeout 10 --max-time 30 \
  --output "$TMP_DIR/response.json" --write-out '%{http_code}' \
  --request POST "$BASE_URL/api/jobs" \
  --header "Authorization: Bearer $TOKEN" \
  --header 'Content-Type: application/json' --data "$BODY")

echo "  HTTP $STATUS"
python3 -m json.tool "$TMP_DIR/response.json" 2>/dev/null || sed 's/^/  /' "$TMP_DIR/response.json"
if [ "$STATUS" != '202' ]; then
  echo '  접수 실패: 토큰, 입력 파일의 소유권, 모델(gpt-6-luna)과 질문 형식을 확인한다.' >&2
  exit 1
fi

JOB_UUID=$(python3 - "$TMP_DIR/response.json" <<'PY'
import json, sys, uuid
try:
    with open(sys.argv[1]) as response:
        job = json.load(response)
    print(uuid.UUID(job["uuid"]))
except (ValueError, KeyError, TypeError):
    sys.exit("접수 응답에 유효한 Job UUID가 없다.")
PY
)

echo
printf '  WATCH=1 BASE_URL=%q ' "$BASE_URL"
if [ -n "${USER_UUID:-}" ]; then
  printf 'USER_UUID=%q ' "$USER_UUID"
fi
printf './scripts/job.sh %s\n' "$JOB_UUID"
