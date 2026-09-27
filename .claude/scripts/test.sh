#!/usr/bin/env bash
# 테스트를 돌리고 결과를 **정직하게** 돌려준다.
#
#   .claude/scripts/test.sh                        # 전체
#   .claude/scripts/test.sh '*PresignedUpload*'    # 클래스 필터
#
# `./gradlew test | tail` 은 파이프의 마지막 명령인 **tail 의 종료 코드**를 남긴다.
# 그래서 컴파일이 깨져도 0 으로 보이고, 그때 `build/test-results` 에는 직전 실행 결과가
# 그대로 남아 있어 집계하면 통과처럼 읽힌다. 실제로 한 번 그렇게 읽을 뻔했다.
#
# 그래서 셋을 한다 — 이전 결과를 지우고, 종료 코드를 그대로 전하고, 집계를 함께 찍는다.
set -euo pipefail
cd "$(dirname "$0")/../.."

FILTER="${1:-}"

# 지우고 시작한다. 남아 있으면 "이번 실행의 결과" 와 "지난번 결과" 를 구분할 수 없다.
rm -rf build/test-results/test

# set -e 가 걸려 있으면 실패 시 여기서 끝나 집계를 못 찍는다. 이 구간만 푼다.
set +e
if [ -n "$FILTER" ]; then
    ./gradlew test --tests "$FILTER"
else
    ./gradlew test
fi
STATUS=$?
set -e

echo
if [ -d build/test-results/test ]; then
    python3 - <<'PY'
import glob, os, re, time

files = glob.glob('build/test-results/test/*.xml')
if not files:
    print("결과 파일이 없다 — 테스트가 돌기 전에 끝났다(대개 컴파일 실패).")
    raise SystemExit

tests = failures = 0
for path in files:
    head = open(path, encoding='utf-8').read(400)
    tests += int(re.search(r'tests="(\d+)"', head).group(1))
    failures += int(re.search(r'failures="(\d+)"', head).group(1))
    failures += int(re.search(r'errors="(\d+)"', head).group(1))

newest = max(os.path.getmtime(p) for p in files)
print(f"테스트 {tests}개 · 실패 {failures}개 · 클래스 {len(files)}개")
# 방금 만들어진 결과인지 눈으로 확인할 수 있게 시각을 함께 찍는다.
print(f"결과 시각 {time.strftime('%H:%M:%S', time.localtime(newest))} (지금 {time.strftime('%H:%M:%S')})")
PY
else
    echo "결과 디렉토리가 없다 — 컴파일 단계에서 끝났다."
fi

echo
if [ "$STATUS" -eq 0 ]; then
    echo "통과 (exit=0)"
else
    echo "실패 (exit=$STATUS) — 위 gradle 출력의 'e:' 줄을 먼저 본다"
fi

# 파이프로 이어도 종료 코드가 살아남도록 그대로 돌려준다.
exit "$STATUS"
