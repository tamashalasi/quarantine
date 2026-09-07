# Quarantine

A simple, local-only Android app that puts a deliberate pause between you and selected apps. Kotlin, Jetpack Compose, and Material 3. Android 10 or newer.

## Use

1. Install the APK. Open Quarantine and enable **app blocking** in Android Accessibility settings, **usage access**, and **notifications**. On some Android versions, sideloaded apps first require **Allow restricted settings** in Android's app-info menu.
2. Hold **Unlock quarantine settings** continuously for 10 seconds. The slider and countdown show the remaining time. Releasing, sliding outside the button, or leaving the screen resets the hold. A focused button also supports holding Space or Enter.
3. Choose a hold time from 1–300 seconds. Tap an app row or checkbox to toggle quarantine. Changes save automatically. Filter by **All**, **In quarantine**, or **Not in quarantine**. Apps sort by seven-day screen time, then name.
4. Opening a quarantined app displays **Unlock quarantine for [app-name]** with the same hold mechanism and screen-time summary.
5. An unlocked app stays unlocked across app switching, screen locking, and Recents dismissal. Tap its **Lock [app-name] in quarantine** notification to revoke access and return Home if it is open. Quarantine also offers **Lock unlocked apps** without requiring a settings unlock.

Settings lock when Quarantine is backgrounded or paused. Temporary app unlocks disappear when the accessibility service/process restarts or the phone reboots. Quarantine membership and hold time remain saved.

## What Android permits

This is a personal-use friction tool. It does not use root, device-owner privileges, or prevent uninstalling or disabling its permissions. Android offers no reliable public callback for another app's dismissal from Recents. Explicit relocking is used instead. The notification returns Home; it cannot force-stop another app, remove its task, or stop background audio/services.

Accessibility access observes application window/package identifiers and draws an unlock overlay. It does not inspect node text, capture screenshots, or collect typed input. Usage Access reads Android's usage aggregates. No internet permission, server, analytics, account, or cloud backup is used.

The app lists launchable apps in the current profile. Quarantine itself, launchers, Android Settings, and essential system UI cannot be selected. Other profiles, hidden apps, notification content, Recents previews, split-screen, and picture-in-picture are not comprehensive blocking boundaries. OEM service restrictions or permission revocation can interrupt protection; the landing screen reports whether the service is active.

Seven-day usage means today plus the previous six local calendar days. Both summaries and sorting use the same foreground-usage data for listed apps. These are Android estimates, not a claim to match Digital Wellbeing: Android can expand query boundaries, retain incomplete history, and account differently for multi-window use. Missing permission/history is shown explicitly. Refresh on the landing/settings screen to reread usage. See [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager) and [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService).

If notifications are denied, disabled by channel, or dismissed by Android, use **Lock unlocked apps** in Quarantine. Opening Quarantine recreates notifications for current grants when notifications are permitted.

## Development

Install [mise](https://mise.jdx.dev/) and activate the pinned JDK:

```sh
mise install java
mise exec -- java -version
```

Install Android SDK command-line tools, accept the SDK licenses, then install the required components:

```sh
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'build-tools;34.0.0' 'platform-tools'
export ANDROID_HOME=/path/to/Android/Sdk
mise exec -- ./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android Studio can open the root directory. The debug APK uses a local debug key and is not the reproducible release artifact. Release builds have no embedded signing key. Production package ID: `app.quarantine.android`.

## Reproduce a GitHub APK exactly

The reference release process uses Linux amd64 containers, Temurin 17.0.18+8, Gradle 8.13, AGP 8.11.1, Kotlin 2.1.20, Android platform 36 revision 2, build-tools 36.0.0, and platform-tools 37.0.1. Container bases, SDK archives, Python dependencies, Gradle distribution, and Maven artifacts are pinned by version and checksum. Automatic SDK downloads are disabled. Signing uses build-tools 34.0.0 for compatibility with apksigcopier 1.1.1.

Prerequisites: Git, Docker with Linux amd64 support, network access for pinned dependencies, and about 8 GB of available memory. On ARM hosts Docker must provide amd64 emulation. Building the SDK container implies accepting the [Android SDK license](https://developer.android.com/studio/terms).

1. Clone this repository and check out the **exact release tag**. Verify that its commit matches `source_commit` in the release's `build-info.json`. Use a clean checkout with no source edits.
2. Download `quarantine.apk`, `SHA256SUMS`, and `build-info.json` from that GitHub release. Save the signed APK as `dist/reference/quarantine.apk`.
3. Build from source. Each invocation starts with a new container, workspace, and Gradle cache:

```sh
SOURCE_COMMIT="$(git rev-parse HEAD)" ./scripts/reproducible-build.sh "$PWD/dist/rebuild"
```

4. Reconstruct the signed APK using the reference APK's **public signing data**. No private signing key is needed:

```sh
docker run --rm --platform linux/amd64 --entrypoint python \
  -v "$PWD:/src:ro" -v "$PWD/dist:/out" quarantine-build \
  /src/scripts/verify-apk.py \
  /out/reference/quarantine.apk \
  /out/rebuild/quarantine-unsigned.apk \
  /out/quarantine-reconstructed.apk
```

The script verifies the reference signature, copies signing metadata onto the locally compiled APK, verifies the reconstructed signature, then requires **identical SHA-256 hashes**. It fails on a signature error or byte mismatch. It does not replace locally compiled code/resources with reference code. Compare the printed signer certificate with a previously trusted release if checking publisher identity; a matching hash alone does not establish who published it.

This follows [F-Droid's signature-copying approach](https://f-droid.org/docs/Reproducible_Builds/). A plain unsigned Gradle output cannot have the signed download's hash. Signing the rebuild with your own key also changes the hash and prevents it from updating the official installation.

To check unsigned determinism independently, rebuild to another directory and compare:

```sh
./scripts/reproducible-build.sh "$PWD/dist/second"
cmp dist/rebuild/quarantine-unsigned.apk dist/second/quarantine-unsigned.apk
```

## Maintaining releases

Create and retain an offline-backed-up RSA signing keystore. Configure GitHub Actions secrets `QUARANTINE_KEYSTORE_BASE64` (single-line base64 keystore), `QUARANTINE_KEY_ALIAS`, `QUARANTINE_STORE_PASSWORD`, and `QUARANTINE_KEY_PASSWORD`. Never commit a private key. Repository creation, secret configuration, and actual publication are maintainer steps.

Update `versionCode` and `versionName` in `app/build.gradle.kts`, wait for the **Check** workflow (including API 29 and 36 device tests) to pass on that commit, then push a matching `v*` tag. **Reproducible release** builds twice in separate clean containers, compares unsigned APKs, signs one, reconstructs the other, and checks the exact signed hash before publishing the APK, checksum, and build information. Release checks intentionally fail if signing secrets are missing.

When changing dependencies, explicitly regenerate verification metadata with `./gradlew --write-verification-metadata sha256 assembleDebug assembleRelease assembleDebugAndroidTest testDebugUnitTest lintDebug`, review new artifacts/checksums, and commit `gradle/verification-metadata.xml`. Do not disable verification to bypass a checksum mismatch. Re-run reproducibility checks after any toolchain/dependency change.

## Tests

```sh
mise exec -- ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
# With an unlocked emulator or physical phone connected:
mise exec -- ./gradlew connectedDebugAndroidTest
```

Unit tests cover hold boundaries/reset, duration validation, independent grants, relocking/restarts, filtering/sorting, totals, and top-three usage. The same Compose and persistence tests run locally with Robolectric 4.16 (API 29) and on devices: continuous holding, early release, cancellation, list toggling/filtering, and saved settings. Robolectric fetches its version-selected Android runtime from Maven Central on the first test run.

See [the device checklist](docs/device-testing.md) for service interception, notification actions, permissions, and Android/OEM scenarios that need interactive verification. Passing unit/UI tests alone does not establish complete app-blocking coverage.

See [the implementation validation record](docs/validation.md) for checks actually run, the reproduced APK hash, and remaining device-test limitations.
