"""Install immutable Linux SDK archives, without selecting mutable SDK channels."""
import hashlib
import io
import os
from pathlib import Path
import urllib.request
import zipfile

ARCHIVES = [
    ("platform-tools_r37.0.1-linux.zip", "d230f13842f60f782a8645f9c813f8f845bf36089ea7289f28c48f17979313f1", "platform-tools"),
    ("platform-36_r02.zip", "37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e", "platforms/android-36"),
    ("build-tools_r36_linux.zip", "5d9ac77fb6ff43d9da518a337b4fcf8f9097113df531d99ccefe80ef7ce8250b", "build-tools/36.0.0"),
    ("build-tools_r34-linux.zip", "e858c4b60069d0431051b225d384413b1643e1289b00a4825aed347f25bd510f", "build-tools/34.0.0"),
]

root = Path(os.environ.get("ANDROID_HOME", "/opt/android-sdk"))
for filename, checksum, destination in ARCHIVES:
    print(f"Installing {filename}", flush=True)
    data = urllib.request.urlopen(f"https://dl.google.com/android/repository/{filename}", timeout=120).read()
    if hashlib.sha256(data).hexdigest() != checksum:
        raise SystemExit(f"Checksum mismatch: {filename}")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for entry in archive.infolist():
            parts = Path(entry.filename).parts[1:]
            if not parts:
                continue
            if ".." in parts:
                raise SystemExit("Unsafe archive path")
            target = root / destination / Path(*parts)
            if entry.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(entry))
                target.chmod((entry.external_attr >> 16) & 0o777 or 0o644)
    for executable in (root / destination).iterdir():
        if executable.is_file() and executable.name in ("aapt", "aapt2", "aidl", "apksigner", "zipalign", "d8", "dexdump", "split-select"):
            executable.chmod(0o755)

# Accepted Android SDK license identifiers; no sdkmanager or unpinned downloads at build time.
(root / "licenses").mkdir(parents=True, exist_ok=True)
(root / "licenses/android-sdk-license").write_text(
    "8933bad161af4178b1185d1a37fbf41ea5269c55\nd56f5187479451eabf01fb78af6dfcb131a6481e\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n"
)
