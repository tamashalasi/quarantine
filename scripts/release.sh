#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
project_root="$PWD"
fail() { echo "Release stopped: $*" >&2; exit 1; }
usage() {
  cat <<'USAGE'
Usage: scripts/release.sh [--skip-tests | --tests-only] [remote]
  (default)     Test the version bump, then commit/tag/push the release.
  --skip-tests  Commit/tag/push the version bump without running tests.
  --tests-only  Test the current checkout; no version prompt or Git operations.
  --help        Show this help.
The release remote defaults to origin. Modes are mutually exclusive.
USAGE
}
mode=release
remote=origin
remote_set=false
for arg in "$@"; do
  case "$arg" in
    --skip-tests|--tests-only)
      [[ "$mode" == release ]] || fail 'Choose only one of --skip-tests and --tests-only.'
      mode="$arg"
      ;;
    --help|-h) usage; exit 0 ;;
    -*) fail "Unknown option: $arg (see --help)." ;;
    *)
      [[ "$remote_set" == false ]] || fail 'Provide at most one remote.'
      remote="$arg"
      remote_set=true
      ;;
  esac
done
if [[ "$mode" == --tests-only ]]; then
  [[ "$remote_set" == false ]] || fail '--tests-only does not use a remote.'
  echo 'Running tests on the current checkout; no version changes or release Git operations.'
  exec bash scripts/test.sh
fi
version="$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)"[[:space:]]*$/\1/p' app/build.gradle.kts)"
[[ "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || fail 'Expected versionName in major.minor.patch format.'
IFS=. read -r major minor patch <<< "$version"
printf 'Current version: %s\n  1) patch → %s.%s.%s\n  2) minor → %s.%s.0\n  3) major → %s.0.0\n' \
  "$version" "$major" "$minor" "$((patch + 1))" "$major" "$((minor + 1))" "$((major + 1))"
while true; do
  read -r -p 'Version update (patch/minor/major, or 1/2/3): ' choice || fail 'No version update selected.'
  case "$choice" in
    patch|1) next="$major.$minor.$((patch + 1))"; break ;;
    minor|2) next="$major.$((minor + 1)).0"; break ;;
    major|3) next="$((major + 1)).0.0"; break ;;
    *) echo 'Choose patch, minor, or major.' ;;
  esac
done
echo "Release version: $version → $next"
[[ -z "$(git status --porcelain --untracked-files=all)" ]] || fail 'Commit or stash all changes before releasing.'
branch="$(git symbolic-ref --quiet --short HEAD)" || fail 'Check out a branch before releasing.'
commit="$(git rev-parse HEAD)"
tag="v$next"
if git show-ref --verify --quiet "refs/tags/$tag"; then fail "Local tag $tag already exists."; fi
git remote get-url "$remote" >/dev/null
remote_tags="$(git ls-remote --tags "$remote" "refs/tags/$tag")"
[[ -z "$remote_tags" ]] || fail "Remote tag $tag already exists."
# Keep the working branch and version untouched if any test fails.
temporary="$(mktemp -d "${TMPDIR:-/tmp}/quarantine-release.XXXXXX")"
checkout="$temporary/source"
cleanup() {
  git -C "$project_root" worktree remove --force "$checkout" >/dev/null 2>&1 || true
  rm -rf -- "$temporary"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
git worktree add --detach "$checkout" "$commit"
python3 - "$checkout/app/build.gradle.kts" "$next" <<'PY'
import re, sys
from pathlib import Path
path = Path(sys.argv[1])
text = path.read_text()
codes = re.findall(r'^\s*versionCode = (\d+)\s*$', text, re.M)
if len(codes) != 1 or int(codes[0]) >= 2100000000:
    raise SystemExit('Expected one valid versionCode below 2100000000.')
text, count = re.subn(r'(?m)^(\s*versionCode = )\d+', lambda m: m[1] + str(int(codes[0]) + 1), text)
text, names = re.subn(r'(?m)^(\s*versionName = ")[^"]+("\s*)$', lambda m: m[1] + sys.argv[2] + m[2], text)
if count != 1 or names != 1:
    raise SystemExit('Expected exactly one versionCode and versionName.')
path.write_text(text)
PY
git -C "$checkout" add app/build.gradle.kts
git -C "$checkout" commit -m "Release $next"
release_commit="$(git -C "$checkout" rev-parse HEAD)"
if [[ "$mode" == --skip-tests ]]; then
  echo "Skipping tests for $tag as requested."
else
  echo "Testing $tag in an isolated checkout..."
  # Keep the full output after the temporary checkout is removed.
  mkdir -p "$project_root/dist/local-tests"
  (cd "$checkout" && bash scripts/test.sh) 2>&1 | tee "$project_root/dist/local-tests/release-$tag.log"
fi
[[ "$(git -C "$checkout" rev-parse HEAD)" == "$release_commit" && -z "$(git -C "$checkout" status --porcelain --untracked-files=all)" ]] || fail 'Release checkout changed during preparation.'
[[ "$(git rev-parse HEAD)" == "$commit" && "$(git symbolic-ref --quiet --short HEAD)" == "$branch" ]] || fail 'Working branch changed during release preparation.'
[[ -z "$(git status --porcelain --untracked-files=all)" ]] || fail 'Working tree changed during release preparation.'
git merge --ff-only "$release_commit"
git tag -a "$tag" "$release_commit" -m "Quarantine $next"
if ! git -c push.followTags=false push --atomic "$remote" \
  "refs/heads/$branch:refs/heads/$branch" "refs/tags/$tag:refs/tags/$tag"; then
  echo "Push failed. Local release commit and tag $tag remain; inspect the remote before retrying." >&2
  exit 1
fi
echo "Published $tag ($version → $next). GitHub will build and sign the APK."
echo "Pushed branch $branch and tag $tag together at $release_commit."
