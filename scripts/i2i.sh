#!/usr/bin/env bash
# 생성 결과 파일을 입력으로 이미지 투 이미지 요청을 보낸다.
#   ./scripts/i2i.sh <결과파일-uuid> "수채화로 바꿔줘"
#   ./scripts/i2i.sh <결과파일-uuid> "수채화로" 2 gpt-image-2
#   RATIO=16:9 RESOLUTION=2k QUALITY=high ./scripts/i2i.sh <결과파일-uuid> "수채화로"
set -euo pipefail

usage() {
  echo '사용법: ./scripts/i2i.sh <생성 결과 파일 UUID> <프롬프트> [작업 수: 1~4] [모델]'
  echo '업로드 발급 UUID와 Job UUID는 입력으로 사용할 수 없다.'
  echo '환경변수: BASE_URL, ACCESS_TOKEN, USER_UUID, RATIO, RESOLUTION, QUALITY'
}

if [ "${1:-}" = '--help' ]; then
  usage
  exit 0
fi
if [ "$#" -lt 2 ] || [ "$#" -gt 4 ]; then
  usage >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"

for tool in curl python3; do
  command -v "$tool" >/dev/null || { echo "$tool 이 필요하다." >&2; exit 1; }
done

# JSON 인코딩을 맡겨 따옴표와 줄바꿈이 들어간 프롬프트도 그대로 전달한다.
BODY=$(python3 - "$1" "$2" "${3:-1}" "${4:-gpt-image-2}" \
  "${RATIO:-1:1}" "${RESOLUTION:-1k}" "${QUALITY:-auto}" <<'PY'
import json, sys, uuid
source, prompt, count, model, ratio, resolution, quality = sys.argv[1:]
try:
    source = str(uuid.UUID(source))
except ValueError:
    sys.exit("생성 결과 파일의 UUID가 필요하다.")
if not prompt.strip():
    sys.exit("프롬프트는 비어 있을 수 없다.")
try:
    count = int(count)
except ValueError:
    sys.exit("작업 수는 1~4의 정수여야 한다.")
if not 1 <= count <= 4:
    sys.exit("작업 수는 1~4여야 한다.")
if not model.strip():
    sys.exit("모델은 비어 있을 수 없다.")
print(json.dumps({
    "option": {
        "type": "image-to-image",
        "prompt": prompt,
        "sources": [{"type": "generated", "uuid": source}],
        "size": {"type": "ratio", "ratio": ratio, "resolution": resolution},
        "quality": quality,
    },
    "model": model,
    "taskCount": count,
}, ensure_ascii=False))
PY
)

TOKEN="${ACCESS_TOKEN:-}"
if [ -z "$TOKEN" ]; then
  TOKEN="$("$REPO_ROOT/scripts/dev-token.sh" "${USER_UUID:-}")"
fi

TMP_DIR="$(mktemp -d -t workbench-i2i.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "  POST $BASE_URL/api/jobs"
printf '  %s\n\n' "$BODY"
STATUS=$(curl --silent --show-error --connect-timeout 10 --max-time 30 \
  --output "$TMP_DIR/response.json" --write-out '%{http_code}' \
  --request POST "$BASE_URL/api/jobs" \
  --header "Authorization: Bearer $TOKEN" \
  --header 'Content-Type: application/json' --data "$BODY")

echo "  HTTP $STATUS"
python3 -m json.tool "$TMP_DIR/response.json" 2>/dev/null || cat "$TMP_DIR/response.json"
if [ "$STATUS" != '202' ]; then
  echo '  접수 실패: 토큰, 생성 결과 파일의 소유권, 모델과 옵션을 확인한다.' >&2
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
