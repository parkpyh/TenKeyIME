#!/bin/bash
set -euo pipefail

VERSION="${1:-}"

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then
  echo "사용법: ./release.sh 1.4"
  exit 1
fi

cd "$(dirname "$0")"

if [ "$(git branch --show-current)" != "main" ]; then
  echo "오류: main 브랜치에서 실행해 주세요."
  exit 1
fi

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "오류: 커밋되지 않은 변경사항이 있습니다."
  git status --short
  exit 1
fi

git fetch origin main --tags

if [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]; then
  echo "오류: 로컬 main과 GitHub main이 다릅니다."
  exit 1
fi

if git rev-parse -q --verify "refs/tags/v$VERSION" >/dev/null; then
  echo "오류: v$VERSION 태그가 이미 존재합니다."
  exit 1
fi

python3 - "$VERSION" <<'PY'
import pathlib
import re
import sys

version = sys.argv[1]
path = pathlib.Path("app/build.gradle.kts")
text = path.read_text()

code = re.search(r"versionCode\s*=\s*(\d+)", text)
name = re.search(r'versionName\s*=\s*"([^"]+)"', text)

if not code or not name:
    sys.exit("오류: 앱 버전 설정을 찾을 수 없습니다.")

if name.group(1) == version:
    sys.exit("오류: 현재 버전과 동일합니다.")

text = re.sub(
    r"versionCode\s*=\s*\d+",
    f"versionCode = {int(code.group(1)) + 1}",
    text,
    count=1
)
text = re.sub(
    r'versionName\s*=\s*"[^"]+"',
    f'versionName = "{version}"',
    text,
    count=1
)

path.write_text(text)

print(f"버전: {name.group(1)} → {version}")
print(f"versionCode: {code.group(1)} → {int(code.group(1)) + 1}")
PY

git add app/build.gradle.kts
git commit -m "chore: release v$VERSION"
git push origin main

git tag "v$VERSION"
git push origin "v$VERSION"

echo
echo "배포 요청 완료: v$VERSION"
echo "GitHub Actions에서 빌드와 릴리스가 진행됩니다."
echo "https://github.com/parkpyh/TenKeyIME/actions"
