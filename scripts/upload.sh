#!/usr/bin/env bash
# 이미지의 presigned URL을 발급받아 보관소에 직접 PUT한다.
#   ./scripts/upload.sh ./image.png
#   BASE_URL=http://localhost:8099 ./scripts/upload.sh ./image.jpg
#   ACCESS_TOKEN=<token> ./scripts/upload.sh ./image.webp
set -euo pipefail

if [ "${1:-}" = "--help" ] || [ "$#" -ne 1 ]; then
  echo "사용법: ./scripts/upload.sh <이미지 파일>"
  echo "환경변수: BASE_URL (기본 http://localhost:8080), ACCESS_TOKEN, USER_UUID"
  [ "${1:-}" = "--help" ] && exit 0
  exit 1
fi

IMAGE="$1"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"

for tool in curl python3; do
  command -v "$tool" >/dev/null || { echo "$tool 이 필요하다." >&2; exit 1; }
done

# 확장자 대신 파일 헤더로 형식을 정한다. 서버의 업로드 후 내용 검증을 대신하지는 않는다.
METADATA=$(python3 - "$IMAGE" <<'PY'
import os, sys
path = sys.argv[1]
if not os.path.isfile(path):
    sys.exit("읽을 수 있는 이미지 파일이 필요하다.")
size = os.path.getsize(path)
if not 1 <= size <= 20 * 1024 * 1024:
    sys.exit("이미지는 1바이트~20MiB여야 한다.")
with open(path, "rb") as image:
    header = image.read(12)
if header.startswith(b"\x89PNG\r\n\x1a\n"):
    content_type = "image/png"
elif header.startswith(b"\xff\xd8\xff"):
    content_type = "image/jpeg"
elif header[:4] == b"RIFF" and header[8:12] == b"WEBP":
    content_type = "image/webp"
else:
    sys.exit("PNG·JPEG·WebP 파일만 지원한다.")
print(content_type, size)
PY
)
read -r CONTENT_TYPE CONTENT_LENGTH <<< "$METADATA"
TOKEN="${ACCESS_TOKEN:-}"
if [ -z "$TOKEN" ]; then
  TOKEN="$("$REPO_ROOT/scripts/dev-token.sh" "${USER_UUID:-}")"
fi

TMP_DIR="$(mktemp -d -t workbench-upload.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

BODY=$(python3 - "$CONTENT_TYPE" "$CONTENT_LENGTH" <<'PY'
import json, sys
print(json.dumps({"contentType": sys.argv[1], "contentLength": int(sys.argv[2])}))
PY
)
echo "  발급 요청  POST $BASE_URL/api/uploads · $CONTENT_TYPE · $CONTENT_LENGTH bytes"
STATUS=$(curl --silent --show-error --connect-timeout 10 --max-time 30 \
  --output "$TMP_DIR/response.json" --write-out '%{http_code}' \
  --request POST "$BASE_URL/api/uploads" \
  --header "Authorization: Bearer $TOKEN" \
  --header 'Content-Type: application/json' --data "$BODY")
if [ "$STATUS" != "200" ]; then
  echo "  발급 실패  HTTP $STATUS (401: 토큰 확인, 501: S3 호환 보관소 설정 확인)" >&2
  exit 1
fi

# 응답의 서명 URL은 출력하지 않고 전송에만 사용한다.
python3 - "$TMP_DIR/response.json" "$CONTENT_TYPE" "$CONTENT_LENGTH" > "$TMP_DIR/issued" <<'PY'
import json, sys, urllib.parse, uuid
with open(sys.argv[1]) as response:
    issued = json.load(response)
headers = issued["headers"]
url = urllib.parse.urlsplit(issued["url"])
if (issued["method"] != "PUT" or url.scheme not in ("http", "https") or not url.netloc
        or headers.get("Content-Type") != sys.argv[2]
        or headers.get("Content-Length") != sys.argv[3]):
    sys.exit("발급 응답의 메서드·주소·헤더가 요청과 다르다.")
print(uuid.UUID(issued["uuid"]))
print(issued["url"])
print(urllib.parse.urlunsplit((url.scheme, url.netloc, url.path, "", "")))
PY
{
  IFS= read -r UPLOAD_UUID
  IFS= read -r UPLOAD_URL
  IFS= read -r MASKED_URL
} < "$TMP_DIR/issued"

echo "  발급 성공  HTTP $STATUS · uuid=$UPLOAD_UUID"
echo "  직접 업로드 PUT $MASKED_URL [서명 생략]"
# Bearer는 앱 API에만 보낸다. --upload-file은 파일을 스트리밍하고 PUT을 사용한다.
STATUS=$(curl --silent --show-error --connect-timeout 10 --max-time 300 \
  --output "$TMP_DIR/put-response" --write-out '%{http_code}' \
  --header "Content-Type: $CONTENT_TYPE" --header "Content-Length: $CONTENT_LENGTH" \
  --upload-file "$IMAGE" "$UPLOAD_URL")
case "$STATUS" in
  2??) echo "  업로드 성공 HTTP $STATUS · $CONTENT_LENGTH bytes" ;;
  *) echo "  업로드 실패 HTTP $STATUS (주소 만료 또는 파일 형식·크기 변경을 확인한다)" >&2; exit 1 ;;
esac
echo "  보관소가 PUT을 수락했다. 앱의 업로드 완료 검증·입력 등록은 아직 제공하지 않는다."
