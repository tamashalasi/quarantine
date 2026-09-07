#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir="${1:-$project_root/dist/rebuild}"
mkdir -p "$output_dir"
output_dir="$(cd -- "$output_dir" && pwd)"
docker build --platform linux/amd64 -f "$project_root/build-support/Dockerfile" -t quarantine-build "$project_root"
docker run --rm --platform linux/amd64 \
  -e SOURCE_COMMIT="${SOURCE_COMMIT:-local-uncommitted}" \
  -v "$project_root:/src:ro" -v "$output_dir:/out" quarantine-build assembleRelease
