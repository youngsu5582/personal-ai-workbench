#!/usr/bin/env bash
# 샘플 페이지의 origin에서 보관소로 직접 PUT할 수 있도록 버킷 CORS 규칙을 추가한다.
#   ./scripts/upload-cors.sh                     # http://localhost:8080
#   ./scripts/upload-cors.sh http://localhost:8099
set -euo pipefail

if [ "${1:-}" = "--help" ] || [ "$#" -gt 1 ]; then
  echo "사용법: ./scripts/upload-cors.sh [페이지 origin] (기본 http://localhost:8080)"
  echo "AWS CLI·curl과 .env의 STORAGE_S3_* 설정이 필요하다. 기존의 다른 CORS 규칙은 유지한다."
  [ "${1:-}" = "--help" ] && exit 0
  exit 1
fi

PAGE_ORIGIN="${1:-http://localhost:8080}"
cd "$(dirname "$0")/.."
for tool in aws curl python3; do
  command -v "$tool" >/dev/null || { echo "$tool 이 필요하다." >&2; exit 1; }
done
# 명시한 환경변수가 .env보다 우선한다.
S3_SETTINGS=(STORAGE_S3_ENDPOINT STORAGE_S3_BUCKET STORAGE_S3_REGION STORAGE_S3_ACCESS_KEY STORAGE_S3_SECRET_KEY STORAGE_S3_PATH_STYLE_ACCESS)
OVERRIDES=()
for setting in "${S3_SETTINGS[@]}"; do
  OVERRIDES+=("${!setting:-}")
done
if [ -f .env ]; then
  set -a; . ./.env; set +a
fi
for index in "${!S3_SETTINGS[@]}"; do
  if [ -n "${OVERRIDES[$index]}" ]; then
    printf -v "${S3_SETTINGS[$index]}" '%s' "${OVERRIDES[$index]}"
  fi
done
: "${STORAGE_S3_ENDPOINT:?STORAGE_S3_ENDPOINT 설정이 필요하다}"
: "${STORAGE_S3_BUCKET:?STORAGE_S3_BUCKET 설정이 필요하다}"
: "${STORAGE_S3_ACCESS_KEY:?STORAGE_S3_ACCESS_KEY 설정이 필요하다}"
: "${STORAGE_S3_SECRET_KEY:?STORAGE_S3_SECRET_KEY 설정이 필요하다}"

PAGE_ORIGIN=$(python3 - "$PAGE_ORIGIN" <<'PY'
import sys, urllib.parse
url = urllib.parse.urlsplit(sys.argv[1])
if (url.scheme not in ("http", "https") or not url.hostname or url.username or url.password
        or "*" in url.netloc or url.path not in ("", "/") or url.query or url.fragment):
    sys.exit("origin은 경로 없는 http(s) 주소여야 한다. 예: http://localhost:8080")
print(f"{url.scheme}://{url.netloc}")
PY
)
export AWS_ACCESS_KEY_ID="$STORAGE_S3_ACCESS_KEY"
export AWS_SECRET_ACCESS_KEY="$STORAGE_S3_SECRET_KEY"
export AWS_PAGER=""
unset AWS_SESSION_TOKEN AWS_SECURITY_TOKEN AWS_PROFILE
AWS_ARGS=(--endpoint-url "$STORAGE_S3_ENDPOINT" --region "${STORAGE_S3_REGION:-garage}")
TMP_DIR="$(mktemp -d -t workbench-upload-cors.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

if ! aws "${AWS_ARGS[@]}" s3api get-bucket-cors --bucket "$STORAGE_S3_BUCKET" \
  > "$TMP_DIR/current.json" 2> "$TMP_DIR/error"; then
  if ! python3 -c 'import sys; sys.exit("NoSuchCORSConfiguration" not in open(sys.argv[1]).read())' "$TMP_DIR/error"; then
    cat "$TMP_DIR/error" >&2
    exit 1
  fi
  echo '{"CORSRules":[]}' > "$TMP_DIR/current.json"
fi

python3 - "$TMP_DIR/current.json" "$PAGE_ORIGIN" > "$TMP_DIR/cors.json" <<'PY'
import json, sys
with open(sys.argv[1]) as current:
    rules = json.load(current).get("CORSRules", [])
# origin별로 식별해서 다른 포트의 샘플이나 기존 규칙을 지우지 않는다.
rule_id = "workbench-upload-" + sys.argv[2]
rules = [rule for rule in rules if rule.get("ID") != rule_id]
rules.insert(0, {
    "ID": rule_id,
    "AllowedOrigins": [sys.argv[2]],
    "AllowedMethods": ["PUT"],
    # 브라우저 preflight는 소문자로 보내고 Garage는 문자열을 대소문자까지 비교한다.
    "AllowedHeaders": ["content-type"],
    "MaxAgeSeconds": 300,
})
print(json.dumps({"CORSRules": rules}))
PY
aws "${AWS_ARGS[@]}" s3api put-bucket-cors --bucket "$STORAGE_S3_BUCKET" \
  --cors-configuration "file://$TMP_DIR/cors.json"

# 설정 저장 성공과 브라우저의 preflight 허용은 다르므로 실제 OPTIONS까지 확인한다.
PREFLIGHT_URL=$(python3 - "$STORAGE_S3_ENDPOINT" "$STORAGE_S3_BUCKET" "${STORAGE_S3_PATH_STYLE_ACCESS:-false}" <<'PY'
import sys, urllib.parse
endpoint = urllib.parse.urlsplit(sys.argv[1])
path = endpoint.path.rstrip("/")
host = endpoint.netloc
if sys.argv[3].lower() == "true":
    path += "/" + sys.argv[2]
else:
    host = sys.argv[2] + "." + host
print(urllib.parse.urlunsplit((endpoint.scheme, host, path + "/cors-upload-probe", "", "")))
PY
)
STATUS=$(curl --silent --show-error --connect-timeout 3 --max-time 5 \
  --request OPTIONS --header "Origin: $PAGE_ORIGIN" \
  --header 'Access-Control-Request-Method: PUT' \
  --header 'Access-Control-Request-Headers: content-type' \
  --output /dev/null --write-out '%{http_code}' "$PREFLIGHT_URL")
case "$STATUS" in
  2??) echo "버킷 $STORAGE_S3_BUCKET: $PAGE_ORIGIN 의 PUT preflight HTTP $STATUS 확인 완료." ;;
  *) echo "CORS 설정은 저장됐지만 preflight가 거절됐다: HTTP $STATUS" >&2; exit 1 ;;
esac
