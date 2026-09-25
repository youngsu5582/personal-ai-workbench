#!/usr/bin/env bash
# 결과물이 어떤 길로 내려오는지 확인한다.
#
#   ./scripts/file.sh <file-uuid>              # files API 만 본다
#   ./scripts/file.sh --job <job-uuid>         # Job 의 파일들을 전부 본다
#   KEEP=1 ./scripts/file.sh <file-uuid>       # 받은 파일을 지우지 않는다
#   BASE_URL=http://localhost:8099 ./scripts/file.sh <file-uuid>
#
# 보관소에 따라 응답이 갈린다. 이 스크립트는 그 차이를 그대로 보여준다.
#   local : 200 + 바이트 (앱이 읽어 내보낸다)
#   s3    : 302 + Location (보관소가 직접 내준다)
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN="$(./scripts/dev-token.sh "${USER_UUID:-}")"

OUT="$(mktemp -t workbench-file)"
trap '[ "${KEEP:-}" = "1" ] || rm -f "$OUT"' EXIT

# 서명 주소는 통행증이 쿼리에 통째로 들어 있어 그대로 찍으면 위험하고, 길어서 읽히지도 않는다.
# 경로와 수명만 남긴다 — 확인해야 할 것은 "어디로 가는가" 와 "얼마나 사는가" 다.
mask() {
  python3 -c '
import sys, urllib.parse as u
for line in sys.stdin:
    p = u.urlsplit(line.strip())
    if not p.query:
        print(line.strip()); continue
    q = dict(u.parse_qsl(p.query))
    expires = q.get("X-Amz-Expires")
    signed = "서명됨" if "X-Amz-Signature" in q else "서명없음"
    print("{}://{}{}  [{}{}]".format(
        p.scheme, p.netloc, p.path, signed,
        ", {}초".format(expires) if expires else ""))
'
}

# ── files API ────────────────────────────────────────────────────────────
check_file() {
  local uuid="$1" url="$BASE_URL/api/files/$1"

  echo "  파일 $uuid"

  # 1) 리다이렉트를 따라가지 않는다. 앱이 무엇을 답하는지 그대로 본다.
  local head status location
  head="$(curl -s -o /dev/null -D - -H "Authorization: Bearer $TOKEN" "$url")"
  status="$(printf '%s' "$head" | awk '/^HTTP/{print $2}' | tail -1)"
  location="$(printf '%s' "$head" | awk 'tolower($1)=="location:"{print $2}' | tr -d '\r')"

  case "$status" in
    302) echo "     앱 응답    302 → $(printf '%s' "$location" | mask)" ;;
    200) echo "     앱 응답    200 (앱이 직접 내보낸다)" ;;
    *)   echo "     앱 응답    HTTP $status"; return 1 ;;
  esac

  # 2) 끝까지 따라가 실제 바이트를 받는다.
  local final size ctype
  final="$(curl -sL -H "Authorization: Bearer $TOKEN" -o "$OUT" \
    -w '%{http_code} %{size_download} %{content_type}' "$url")"
  read -r status size ctype <<<"$final"
  echo "     내려받기   HTTP $status  $(printf "%'d" "$size") bytes  $ctype"

  # 3) 키가 내용 주소라 파일명이 곧 체크섬이다. 받은 바이트로 검증한다.
  if [ -n "$location" ]; then
    local expected actual
    expected="$(printf '%s' "$location" | sed -E 's/\?.*//; s|.*/||; s/\.[^.]*$//')"
    actual="$(shasum -a 256 "$OUT" | cut -d' ' -f1)"
    if [ "$expected" = "$actual" ]; then
      echo "     무결성     sha256 일치 (${actual:0:16}…)"
    else
      echo "     무결성     ✗ 키=${expected:0:16}… 받은것=${actual:0:16}…"
    fi
  fi

  # 4) 인가가 앞에 있는지. 토큰 없이 부르면 거부되어야 한다.
  echo "     토큰 없이  HTTP $(curl -s -o /dev/null -w '%{http_code}' "$url")"
}

# ── previewUrl ───────────────────────────────────────────────────────────
# <img src> 가 보내는 것과 같은 요청이다 — 헤더를 싣지 않는다.
check_preview() {
  local preview="$1"
  [ -n "$preview" ] || { echo "     previewUrl 없음 (보관소가 서명할 수 없다 → url 로 폴백)"; return 0; }

  echo "     previewUrl $(printf '%s' "$preview" | mask)"
  local result status size ctype
  result="$(curl -s -o "$OUT" -w '%{http_code} %{size_download} %{content_type}' "$preview")"
  read -r status size ctype <<<"$result"
  echo "     헤더 없이  HTTP $status  $(printf "%'d" "$size") bytes  $ctype"
}

# ── 진입점 ───────────────────────────────────────────────────────────────
if [ "${1:-}" = "--job" ]; then
  JOB_UUID="${2:?사용법: ./scripts/file.sh --job <job-uuid>}"

  curl -sS -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/jobs/$JOB_UUID" \
    | python3 -c '
import json, sys
job = json.load(sys.stdin)
for task in job.get("tasks", []):
    for f in task.get("files", []):
        print(f["uuid"], f.get("previewUrl") or "")
' | while read -r uuid preview; do
    check_file "$uuid"
    check_preview "$preview"
    echo
  done
else
  check_file "${1:?사용법: ./scripts/file.sh <file-uuid>  또는  --job <job-uuid>}"
fi
