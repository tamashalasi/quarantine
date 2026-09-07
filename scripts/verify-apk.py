#!/usr/bin/env python3
"""Reconstruct and verify a signed release using a freshly built unsigned APK."""
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import apksigcopier

if len(sys.argv) != 4:
    raise SystemExit("Usage: verify-apk.py RELEASE.apk REBUILT-UNSIGNED.apk RECONSTRUCTED.apk")
signed, unsigned, reconstructed = map(Path, sys.argv[1:])
if reconstructed.resolve() in (signed.resolve(), unsigned.resolve()):
    raise SystemExit("Output must differ from both input files")
apksigner = Path(os.environ["ANDROID_HOME"]) / "build-tools/34.0.0/apksigner"
subprocess.run([str(apksigner), "verify", "--verbose", "--print-certs", str(signed)], check=True)
# Preserve AGP's 16 KiB native-library alignment, rather than realigning ZIP entries.
apksigcopier.skip_realignment = True
apksigcopier.do_copy(str(signed), str(unsigned), str(reconstructed))
subprocess.run([str(apksigner), "verify", "--verbose", str(reconstructed)], check=True)
expected = hashlib.sha256(signed.read_bytes()).hexdigest()
actual = hashlib.sha256(reconstructed.read_bytes()).hexdigest()
if expected != actual:
    raise SystemExit(f"SHA-256 mismatch: release={expected}, reconstructed={actual}")
print(f"Verified exact SHA-256 match: {actual}")
