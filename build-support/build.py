"""Container entry point: build a clean source copy at a fixed path."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

source, workspace, output = Path("/src"), Path("/workspace"), Path("/out")
shutil.copytree(source, workspace, dirs_exist_ok=True, ignore=shutil.ignore_patterns(
    ".git", ".gradle", ".kotlin", ".idea", "build", "dist", "local.properties", ".tools", "*.jks", "*.keystore"
))
os.chdir(workspace)
Path("gradlew").chmod(0o755)
tasks = sys.argv[1:] or ["assembleRelease"]
result = subprocess.run(["./gradlew", "--no-daemon", "--no-build-cache", "--dependency-verification", "strict", *tasks])
output.mkdir(parents=True, exist_ok=True)
for reports, destination in ((Path("app/build/reports"), "reports"), (Path("build/reports"), "build-reports")):
    if reports.exists():
        shutil.copytree(reports, output / destination, dirs_exist_ok=True)
if result.returncode:
    raise SystemExit(result.returncode)
apk = Path("app/build/outputs/apk/release/app-release-unsigned.apk")
if apk.exists():
    target = output / "quarantine-unsigned.apk"
    shutil.copyfile(apk, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (output / "unsigned.sha256").write_text(f"{digest}  quarantine-unsigned.apk\n")
    inputs = sorted(p for p in workspace.rglob("*") if p.is_file() and
                    not any(part in {"build", ".gradle", ".kotlin"} for part in p.relative_to(workspace).parts))
    source_digest = hashlib.sha256()
    for path in inputs:
        source_digest.update(str(path.relative_to(workspace)).encode() + b"\0" + path.read_bytes())
    (output / "build-info.json").write_text(json.dumps({
        "source_commit": os.environ.get("SOURCE_COMMIT", "local-uncommitted"),
        "source_tree_sha256": source_digest.hexdigest(),
        "unsigned_apk_sha256": digest,
        "java": "Temurin 17.0.18+8", "gradle": "8.13", "agp": "8.11.1", "kotlin": "2.1.20",
        "compile_sdk": "36 revision 2", "build_tools": "36.0.0", "signing_tools": "34.0.0", "platform_tools": "37.0.1",
        "build_container_recipe_sha256": hashlib.sha256(Path("build-support/Dockerfile").read_bytes()).hexdigest(),
    }, indent=2) + "\n")
debug = Path("app/build/outputs/apk/debug/app-debug.apk")
if debug.exists():
    shutil.copyfile(debug, output / "quarantine-debug.apk")
