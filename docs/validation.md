# Validation — 2026-09-07

The following checks were performed during implementation:

| Check | Result |
| --- | --- |
| Kotlin debug/release builds and instrumented test APK | Passed |
| Android lint with warnings treated as errors | Passed, no issues |
| Strict Gradle dependency verification | Passed |
| Core rules tests | 10 passed |
| Compose hold, cancellation, and filtering tests on Robolectric API 29 | 4 passed |
| Settings persistence and activity-pause relocking on Robolectric API 29 | 2 passed |
| Full 16-test JVM suite replayed in the Docker build toolchain | Passed |
| Two independent container builds | Byte-identical unsigned APKs |
| Independent rebuild plus public signature copying | Exact signed SHA-256 match; signature verified |
| Deliberately modified rebuild | Correctly rejected by verification script |
| GitHub workflow YAML and shell script syntax | Passed |

Unsigned release APK SHA-256:

```text
3e03c2c35b8fe45df7c9dde089d66e1ba1a072914fe9a1d822fc2b148baacc26
```

The same unsigned hash was obtained in the local development workspace and both container builds. One container bootstrapped the checksum manifest; the other built with strict verification. The checksum sets were compared and had no conflicting hashes or container-only artifacts. Signing/reconstruction used an ephemeral **test key**, not a production release identity. No GitHub release was published.

Android 16 and Android 10 software emulators were attempted without KVM. Android 16 did not finish booting; Android 10's framework restarted during boot and never became stable enough to run the device suite. Instrumented tests compile, but passing Robolectric tests does **not** establish real-device Accessibility interception or notification behavior. The [device acceptance checklist](device-testing.md) remains to be exercised on actual Android devices or accelerated emulators. GitHub Actions is configured to run the shared device tests on API 29 and 36.

The installable local development artifact is `dist/quarantine-debug.apk`. It uses a development signing key and is separate from the unsigned reproducible release artifact. Build outputs and detailed local reports are intentionally ignored by Git.
