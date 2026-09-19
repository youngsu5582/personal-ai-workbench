#!/usr/bin/env bash
# Job 의 진행 상황을 묻는다.
#
#   ./scripts/job.sh <job-uuid>
#   WATCH=1 ./scripts/job.sh <job-uuid>     # 끝날 때까지 지켜본다
#   INTERVAL=5 WATCH=1 ./scripts/job.sh <job-uuid>
set -euo pipefail
cd "$(dirname "$0")/.."

JOB_UUID="${1:?사용법: ./scripts/job.sh <job-uuid>}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
# 장당 15초쯤 걸린다. 더 자주 물어도 얻는 것이 없다.
INTERVAL="${INTERVAL:-3}"

TOKEN="$(./scripts/dev-token.sh "${USER_UUID:-}")"

fetch() {
  curl -sS -w '\n%{http_code}' \
    -H "Authorization: Bearer $TOKEN" \
    "$BASE_URL/api/jobs/$JOB_UUID"
}

# status 와 progress 만 한 줄로 뽑는다. 전체 본문은 마지막에 한 번만 보여준다.
summarize() {
  python3 -c '
import json, sys
try:
    job = json.loads(sys.stdin.read())
except ValueError:
    sys.exit(1)
p = job.get("progress", {})
print("{} 성공={} 실패={} 남음={}/{}".format(
    job.get("status"), p.get("succeeded"), p.get("failed"),
    p.get("remaining"), p.get("total")))
'
}

while :; do
  RESPONSE="$(fetch)"
  STATUS_CODE="$(printf '%s' "$RESPONSE" | tail -1)"
  PAYLOAD="$(printf '%s' "$RESPONSE" | sed '$d')"

  if [ "$STATUS_CODE" != "200" ]; then
    echo "  HTTP $STATUS_CODE"
    printf '%s' "$PAYLOAD" | python3 -m json.tool 2>/dev/null | sed 's/^/  /' || echo "  $PAYLOAD"
    exit 1
  fi

  LINE="$(printf '%s' "$PAYLOAD" | summarize)" || { echo "  응답을 읽지 못했다: $PAYLOAD"; exit 1; }
  echo "  $(date '+%H:%M:%S')  $LINE"

  # CLOSED 는 "더 움직이지 않는다" 는 뜻이다. 성공했다는 뜻이 아니다.
  case "$LINE" in CLOSED*) break;; esac
  [ "${WATCH:-}" = "1" ] || break
  sleep "$INTERVAL"
done

# Task 를 자리째 보여준다. 실패한 자리도 목록에 남는다.
printf '%s' "$PAYLOAD" | BASE_URL="$BASE_URL" python3 -c '
import json, os, sys
base = os.environ["BASE_URL"]
job = json.loads(sys.stdin.read())
print()
for task in job.get("tasks", []):
    head = "  #{} {}".format(task["sequence"], task["status"])
    if task.get("failureReason"):
        head += "  ({})".format(task["failureReason"])
    print(head)
    for f in task.get("files", []):
        print("     {}{}  {}x{}  {:,} bytes".format(
            base, f["url"], f["width"], f["height"], f["fileSize"]))
'

# 받으려면 토큰이 필요하다. 서명된 URL 과 달리 매 요청마다 소유권을 확인한다.
echo
echo "  내려받기:  curl -H \"Authorization: Bearer \$(./scripts/dev-token.sh)\" -o out.png <url>"
