#!/usr/bin/env bash
# 홈서버 overlay 의 이미지 digest 를 배포할 커밋의 것으로 바꾸고, 배포 PR 본문을 만든다.
#
#   .claude/scripts/deploy-pr.sh <커밋> <PR 본문 파일> [<변경 목록 파일>]
#
# 태그 워크플로(deploy-pr.yml)가 부르고, 손으로 돌려도 같다. 파일만 바꾸고 커밋 · push 는 하지 않는다.
# 지금 떠 있는 커밋은 고정된 digest 이미지의 revision 라벨에서 읽는다 — 태그를 붙였다고 배포된 것은 아니라서
# 직전 태그가 아니라 Git 에 적힌 digest 를 기준으로 삼는다.
set -euo pipefail

TARGET="$(git rev-parse --verify "${1:?배포할 커밋이 필요하다}^{commit}")"
BODY="${2:?PR 본문을 쓸 파일이 필요하다}"
NOTES="${3:-/dev/null}"

REPO=youngsu5582/personal-ai-workbench
IMAGE=ghcr.io/$REPO
OVERLAY=deploy/overlays/home/kustomization.yaml
MIGRATIONS=src/main/resources/db/migration
# image.yml 의 metadata-action(type=sha)이 붙이는 태그. 7자리로 고정이다.
SHORT="${TARGET:0:7}"

fail() { echo "$*" >&2; exit 1; }
inspect() { docker buildx imagetools inspect "$1" --format "{{json .$2}}"; }

# main 에 연달아 머지되면 image.yml 의 앞 실행이 취소돼, 이미지가 없는 커밋이 생긴다.
NEXT="$(inspect "$IMAGE:sha-$SHORT" Manifest 2>/dev/null | jq -r '.digest // empty')" || true
[ -n "$NEXT" ] || fail "이미지가 없다: $IMAGE:sha-$SHORT — image.yml 이 이 커밋에서 끝났는지 본다. 끝난 뒤 다시 돌린다."

CURRENT="$(sed -n 's/^ *digest: *\(sha256:[0-9a-f]\{64\}\).*/\1/p' "$OVERLAY")"
[ -n "$CURRENT" ] || fail "$OVERLAY 에서 digest 를 찾지 못했다."
[ "$CURRENT" != "$NEXT" ] || fail "이미 $IMAGE:sha-$SHORT 의 digest 가 고정돼 있다."

BASE="$(inspect "$IMAGE@$CURRENT" Image | jq -r '.config.Labels["org.opencontainers.image.revision"] // empty')"
[ -n "$BASE" ] || fail "고정된 이미지($CURRENT)에서 커밋을 읽지 못했다."
git cat-file -e "$BASE^{commit}" 2>/dev/null || fail "떠 있는 커밋 $BASE 가 이 저장소 이력에 없다 — 전체 이력을 받았는지 본다."
# 옛 커밋으로 돌아가는 배포는 마이그레이션이 되돌려지지 않아 따로 판단한다. deploy/README.md 를 따른다.
git merge-base --is-ancestor "$BASE" "$TARGET" || fail "${BASE:0:7} 뒤의 커밋이 아니다 — 되돌리기는 이 스크립트가 다루지 않는다."

sed "s/$CURRENT/$NEXT/" "$OVERLAY" > "$OVERLAY.tmp" && mv "$OVERLAY.tmp" "$OVERLAY"

# main 의 한 줄 이력. PR 로 들어온 것은 PR 번호와 제목으로, 바로 올린 커밋은 커밋 제목으로 적는다.
# 스택 PR 을 한 번에 머지하면 머지 커밋 하나에 PR 이 여럿이다. 커밋 제목은 맨 위 PR 만, GitHub 의
# "커밋에 연결된 PR" 은 아래 PR 만 말해서 둘을 합친다.
changes() {
    local sha subject numbers n
    for sha in $(git rev-list --first-parent --reverse "$BASE..$TARGET"); do
        subject="$(git log -1 --format=%s "$sha")"
        numbers="$( { if [[ "$subject" =~ ^Merge\ pull\ request\ \#([0-9]+) ]]; then echo "${BASH_REMATCH[1]}"; fi
            gh api "repos/$REPO/commits/$sha/pulls" --jq ".[] | select(.merge_commit_sha == \"$sha\") | .number"; } | sort -nu)" \
            || fail "GitHub 에서 ${sha:0:7} 의 PR 을 읽지 못했다 — GH_TOKEN 을 본다."
        if [ -z "$numbers" ]; then echo "- \`${sha:0:7}\` $subject"; continue; fi
        for n in $numbers; do
            echo "- #$n $(gh api "repos/$REPO/pulls/$n" --jq .title)"
        done
    done
}

# 기존 행이나 테이블을 바꾸는 문장이 있는지 본다. 새 테이블만 만드는 마이그레이션은 백업 없이도 되돌리기 쉽다.
migrations() {
    local status path label
    git diff --name-status "$BASE" "$TARGET" -- "$MIGRATIONS" | while read -r status path; do
        # 적용된 마이그레이션이 바뀌면 Flyway 체크섬 검증에서 기동이 멈춘다(AGENTS.md).
        case "$status" in A) label=추가 ;; M) label="**수정 — 기동 실패**" ;; D) label=삭제 ;; *) label="$status" ;; esac
        if [ "$status" = D ]; then echo "| \`$(basename "$path")\` | $label | — |"; continue; fi
        if git show "$TARGET:$path" | grep -qiE '^[[:space:]]*(update|delete|insert|alter|drop|truncate)[[:space:]]'; then
            echo "| \`$(basename "$path")\` | $label | **있음** |"
        else
            echo "| \`$(basename "$path")\` | $label | 없음 |"
        fi
    done
}

CHANGES="$(changes)"
MIGRATION_ROWS="$(migrations)"
CONFIG="$(git diff --name-only "$BASE" "$TARGET" -- src/main/resources/application.yaml deploy ":(exclude)$OVERLAY" | sed 's/.*/- `&`/')"
TAG="${GITHUB_REF_NAME:-}"

{
    echo "## 배포할 이미지"
    echo
    echo "홈서버에 띄울 앱 이미지를 \`sha-${BASE:0:7}\` 에서 **\`sha-$SHORT\`** 로 올립니다.${TAG:+ 태그 \`$TAG\` 에서 만들었습니다.}"
    echo
    echo "| | 이미지 | digest |"
    echo "|---|---|---|"
    echo "| 지금 | \`$IMAGE:sha-${BASE:0:7}\` | \`$CURRENT\` |"
    echo "| 바꿀 것 | \`$IMAGE:sha-$SHORT\` | \`$NEXT\` |"
    echo
    echo "## 들어가는 변경 (\`${BASE:0:7}..$SHORT\`)"
    echo
    echo "$CHANGES"
    echo
    echo "## 마이그레이션"
    echo
    if [ -n "$MIGRATION_ROWS" ]; then
        echo "| 파일 | 상태 | 기존 행 · 테이블을 바꾸는 문장 |"
        echo "|---|---|---|"
        echo "$MIGRATION_ROWS"
        echo
        echo "앱이 기동하면서 Flyway 가 적용합니다. 문장 검사는 첫 단어만 보므로, **있음** 이 하나라도 있으면 apply 전에 DB 를 백업합니다."
    else
        echo "없습니다."
    fi
    echo
    echo "## 설정 · 매니페스트"
    echo
    if [ -n "$CONFIG" ]; then
        echo "$CONFIG"
        echo
        echo "새 환경 변수 · Secret 이 필요한지 보고, \`kubectl diff\` 에 Deployment 말고 무엇이 바뀌는지 봅니다."
    else
        echo "\`application.yaml\` · \`deploy/\` 에 바뀐 것이 없습니다. 이미지만 바뀝니다."
    fi
    echo
    echo "## 머지 뒤 홈서버"
    echo
    echo '```bash'
    echo 'cd ~/personal-ai-workbench-deploy && git pull'
    if [ -n "$MIGRATION_ROWS" ]; then
        echo "sudo k3s kubectl -n workbench exec postgresql-0 -- sh -c 'pg_dump -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -Fc' > ~/workbench-\$(date +%Y%m%d-%H%M).dump"
    fi
    echo 'sudo k3s kubectl diff -k deploy/overlays/home'
    echo 'sudo k3s kubectl apply -k deploy/overlays/home'
    echo 'sudo k3s kubectl -n workbench rollout status deployment/workbench'
    echo '```'
    echo
    echo "\`Recreate\` 전략이라 기존 Pod 를 내린 뒤 새 Pod 가 Ready 가 될 때까지(Flyway 포함) 앱이 끊깁니다."
} > "$BODY"

{
    echo "$CHANGES"
    if [ -n "$MIGRATION_ROWS" ]; then
        echo
        echo "마이그레이션: $(git diff --name-only "$BASE" "$TARGET" -- "$MIGRATIONS" | xargs -n1 basename | sed 's/.*/`&`/' | paste -sd ' ' -)"
    fi
} > "$NOTES"

echo "$OVERLAY: sha-${BASE:0:7} → sha-$SHORT ($NEXT)"
