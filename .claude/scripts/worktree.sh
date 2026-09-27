#!/usr/bin/env bash
# 작업용 워크트리를 만든다.
#
#   .claude/scripts/worktree.sh feat/presigned-upload
#   .claude/scripts/worktree.sh fix/token-expiry origin/main    # base 지정 (기본: origin/main)
#
# `git worktree add -b <새브랜치> <경로> <base>` 는 **base 를 upstream 으로 잡는다.**
# 그러면 `git status` 가 "origin/main 보다 N 앞섬" 이라고 말하는데, 이 문장이
# "main 에 올렸다" 로 읽힌다. 실제로 두 번 그렇게 읽혔다. 만들자마자 끊는다.
#
# `.env` 는 gitignore 라 따라오지 않는다. 앱 기동과 live 테스트에 필요해 같이 복사한다.
# 새 워크트리에는 `var/` 도 없다 — 거기서 만든 결과물은 거기에만 있다.
set -euo pipefail

BRANCH="${1:?브랜치 이름이 필요하다. 예: .claude/scripts/worktree.sh feat/foo}"
BASE="${2:-origin/main}"

# 워크트리 안에서 불러도 **주 저장소**를 기준으로 삼는다.
# --git-common-dir 은 워크트리에서도 주 저장소의 .git 을 가리킨다.
COMMON="$(git rev-parse --git-common-dir)"
MAIN_REPO="$(cd "$(dirname "$COMMON")" && pwd)"

# 워크트리는 저장소 **옆에** 모은다. 안에 두면 빌드와 검색이 자기 복사본을 다시 훑는다.
DEST="${MAIN_REPO}-worktrees/$(basename "$BRANCH")"

if [ -e "$DEST" ]; then
    echo "이미 있다: $DEST" >&2
    exit 1
fi

git -C "$MAIN_REPO" fetch origin "${BASE#origin/}"
git -C "$MAIN_REPO" worktree add -b "$BRANCH" "$DEST" "$BASE"

# base 가 upstream 으로 잡힌 것을 끊는다. 첫 푸시에서 -u 로 제자리를 잡는다.
git -C "$DEST" branch --unset-upstream 2>/dev/null || true

if [ -f "$MAIN_REPO/.env" ]; then
    cp "$MAIN_REPO/.env" "$DEST/.env"
    echo ".env 를 복사했다"
fi

echo
echo "만들었다: $DEST"
echo "  브랜치: $BRANCH  (base: $BASE)"
echo "  첫 푸시: git push -u origin $BRANCH"
echo "  정리:   git -C $MAIN_REPO worktree remove $DEST"
