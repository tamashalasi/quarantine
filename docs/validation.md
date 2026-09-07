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
| Native instrumentation suite on KVM Android 10 / API 29 | 6 passed |
| Native instrumentation suite on KVM Android 16 / API 36 | 6 passed after clearing a startup System UI ANR |
| API 29 Accessibility blocking, hold-to-unlock, and notification relocking | Passed manual emulator smoke test |
| Full 16-test JVM suite replayed in the Docker build toolchain | Passed |
| Local workflow Docker build, unit tests, and lint | Passed, all 99 tasks executed |
| Workflow preparation and instrumented tests on KVM API 29 | 6 passed, none failed or skipped |
| Workflow preparation and instrumented tests on KVM API 36 | 6 passed, none failed or skipped |
| Two independent container builds | Byte-identical unsigned APKs |
| Independent rebuild plus public signature copying | Exact signed SHA-256 match; signature verified |
| Deliberately modified rebuild | Correctly rejected by verification script |
| GitHub workflow YAML and shell script syntax | Passed |

Unsigned release APK SHA-256:

```text
3e03c2c35b8fe45df7c9dde089d66e1ba1a072914fe9a1d822fc2b148baacc26
```

The same unsigned hash was obtained in the local development workspace and both container builds. One container bootstrapped the checksum manifest; the other built with strict verification. The checksum sets were compared and had no conflicting hashes or container-only artifacts. Signing/reconstruction used an ephemeral **test key**, not a production release identity. No GitHub release was published.

Initial Android 16 and Android 10 software emulator attempts without KVM failed to become usable. After KVM became available, the API 29 and API 36 native suites and an API 29 manual Accessibility/notification smoke test passed (details below). The broader [device acceptance checklist](device-testing.md), including OEM behavior, remains pending on physical devices. The local `scripts/test.sh` runner now runs the shared device tests on API 29 and 36 before release; the standalone GitHub testing workflow has been removed.

The installable local development artifact is `dist/quarantine-debug.apk`. It uses a development signing key and is separate from the unsigned reproducible release artifact. Build outputs and detailed local reports are intentionally ignored by Git.

## Software emulator retry

A subsequent API 29 retry used emulator 37.1.11, a 480×800 display, 3 GiB RAM, one emulated CPU, SwiftShader, disabled Vulkan, and no snapshots or concurrent builds initiated by this test. Hardware acceleration remained unavailable (`/dev/kvm` absent).

The first boot reported `sys.boot_completed=1`, but System UI displayed an ANR dialog and APK installation did not complete. Logs also identified a setup-app crash caused by the automatically detected timezone `Unknown/Unknown`. Restarting with `-timezone Etc/UTC` corrected the emulator's `qemu.timezone` property, but Android then crashed `system_server` with `IllegalStateException: Wait for admin data timed out` in `AppStandbyController`. The failed emulator was stopped.

No native instrumentation tests or Quarantine interception checks completed during the unaccelerated retry. Logs and the System UI ANR screenshot are retained locally in `dist/emulator-retry/`.

## KVM emulator validation

After the host gained a usable `/dev/kvm`, the same API 29 AVD booted successfully with two CPUs and `-accel on -timezone Etc/UTC`. The debug APK and instrumentation APK installed successfully. Running `am instrument -w app.quarantine.android.test/androidx.test.runner.AndroidJUnitRunner` returned **OK (6 tests)** in 18.786 seconds. This covers continuous hold, early-release cancellation, disabling during a hold, app filtering/toggling, settings persistence, and settings relocking when the activity pauses.

With usage access and Accessibility enabled on the emulator, manual ADB input and screenshot inspection additionally verified:

- The default 10-second settings hold opens settings.
- Editing the duration to 2 seconds and selecting Clock persists the configuration.
- Launching Clock presents a focused `TYPE_ACCESSIBILITY_OVERLAY` with the app-specific unlock label and screen-time summary.
- A completed 2-second hold removes the overlay and posts the Clock relock notification.
- Tapping the notification's **Lock now** action returns to Home; reopening Clock restores the gate.
- A later completed hold survives switching through Home and back to Clock, as designed.
- Returning to Quarantine shows locked settings, the updated hold duration, the seven-day total, and three most-used apps.

No application code changes were needed for these checks. Native test output, screenshots, Accessibility window dumps, and notification evidence are in `dist/emulator-retry/`.

The API 36 Google APIs AVD also booted with KVM and installed both APKs. Its first native run reported 3/6 failures: a System UI ANR dialog owned window focus, so all three hold tests encountered the intentionally disabled hold button. The screenshot and failed output were preserved. After setting the emulator display to 480×800 at density 160, disabling window animations, dismissing the startup dialog, and confirming Quarantine held window focus, the unchanged suite returned **OK (6 tests)** in 25.316 seconds. API 36 interception and notification permission flows were not manually exercised in this session.

The tested debug APK matches `dist/quarantine-debug.apk`, SHA-256 `94c9e009beda9a2df92383c13523cf7b143fc15f3c97977f07739d441f459899`. APK checksums and OS build fingerprints accompany the local evidence.

## Local workflow validation

The workflow's Docker build and both device-test matrix entries passed locally after KVM became available. SDK installation, AVD creation, emulator boot, and `connectedDebugAndroidTest` succeeded on API 29 and 36 with strict dependency verification. Each emulator ran six tests with no failures or skips. The clean Docker build executed all 99 tasks, including debug/release builds, 16 JVM tests, and lint.

The workflow shell commands were executed directly. Local adaptations used the installed SDK through a `cmdline-tools/latest` symlink, isolated AVD directories recreated with `--force`, and separate emulator ports selected through `ANDROID_SERIAL`. GitHub checkout, Java setup, and artifact upload actions were not emulated. Both test emulators were stopped afterward. Logs, per-API test reports, and the detailed run record are in `dist/check/`.

## Local release gate validation

The release script now prompts for patch/minor/major, shows old/new versions, increments `versionCode`, and tests an isolated release commit before updating the working branch and publishing its annotated tag. Five regression tests against temporary repositories and local bare remotes passed, covering version choices, test failure, dirty worktrees, existing remote tags, and rejected pushes. No real release was published during validation.

Debug/release compilation, strict dependency verification, all 16 JVM tests, and lint passed. After correcting emulator startup setup, the native runner passed all six tests on API 29 (24.087 seconds) and API 36 (63.347 seconds), then shut down both emulators. Earlier API 36 runs encountered System UI ANR dialogs that stole focus. The runner now uses Android CTS-style temporary error-dialog suppression, closes existing system dialogs, checks app window focus, restores the dialog setting, and retains logcat. This is instrumentation setup, not evidence that Android's startup ANRs have been fixed.

The standalone GitHub testing workflow was removed; the release workflow retains reproducible builds, signing, and publication. Local runner reports are under `dist/local-tests/`. Build/JVM/lint checks and the corrected native stage were validated separately while refining the runner.
