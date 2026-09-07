# Quarantine

Quarantine helps you pause before opening distracting apps. Choose the apps you want to limit, then hold an unlock button before using them. The wait gives you a moment to decide whether you really want to continue.

For **Android 10 or newer**. No account, ads, or internet connection needed to use the app.

## Get started

1. [Download the latest release](https://github.com/tamashalasi/quarantine/releases/latest) and install `quarantine.apk`.
2. Open Quarantine and follow its setup prompts. Allow **Accessibility access** to block apps, **usage access** to show screen time, and **notifications** to relock apps easily.
3. Hold **Unlock quarantine settings** for 10 seconds, then select your apps. You can change the waiting time to anything from 1 to 300 seconds.

When you open a selected app, its unlock screen appears. Keep holding the button until the countdown finishes; releasing early resets it. Both unlock screens show your screen time over the past seven days and your three most-used apps. The app list is sorted by screen time, with filters for quarantined and other apps.

**Unlocked apps stay unlocked until you relock them**, even if you switch apps or close them from the recent-apps screen. Tap the app's **Lock now** notification, or **Lock unlocked apps** inside Quarantine. Settings lock when you leave Quarantine. Restarting your phone also clears temporary unlocks, while keeping your chosen apps and waiting time saved.

If Android prevents you from granting Accessibility access after installation, open Quarantine's Android app-info menu and look for **Allow restricted settings**.

## Privacy and limits

Your choices and screen-time information stay on your phone. Quarantine has no server, analytics, or cloud backup, and does not read what you type or the contents of other apps.

Quarantine is a tool for changing your own habits. You can disable or uninstall it. It does not stop background music, hide notification previews, or reliably cover every split-screen or floating-window situation. Essential apps, such as Android Settings and your home screen, remain available. Screen-time figures are Android's estimates and may differ from other trackers.

---

## Development and reproducible builds

Built with Kotlin, Jetpack Compose, and Material 3. Package ID: `app.quarantine.android`. Open the repository in Android Studio, or use the commands below.

### Build and install

Install [mise](https://mise.jdx.dev/) and the [Android SDK command-line tools](https://developer.android.com/studio#command-tools), then configure your SDK path:

```sh
mise install java
export ANDROID_HOME="$HOME/Android/Sdk" # Adjust to your installation.
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
# Replace "latest" with your installed command-line tools directory if needed.
sdkmanager --licenses
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'build-tools;34.0.0' 'platform-tools'
mise exec -- ./gradlew assembleDebug testDebugUnitTest lintDebug
```

**Start an emulator or connect a phone before installing.** Building alone does not create an ADB connection. Follow Android's [emulator setup](https://developer.android.com/studio/run/emulator-commandline) or [USB debugging setup](https://developer.android.com/studio/run/device).

For a Linux x86-64 emulator, this one-time setup creates a development device (skip creation if it already exists):

```sh
sdkmanager 'emulator' 'system-images;android-29;default;x86_64'
avdmanager create avd --name quarantine-dev --package 'system-images;android-29;default;x86_64'
# Answer "no" to a custom hardware profile.
emulator -accel-check # Requires usable KVM on Linux.
emulator -avd quarantine-dev -port 5554 -accel on -cores 2 -memory 3072 \
  -skin 480x800 -gpu swiftshader -feature -Vulkan -timezone Etc/UTC -no-snapshot
```

Leave the emulator running, wait for Android to boot, and unlock its screen. In another terminal with the same SDK/PATH settings:

```sh
adb devices -l
# Replace SERIAL with a listed device whose state is "device".
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL shell am start -n app.quarantine.android/.MainActivity
```

`no devices/emulators found` means you need to start an emulator or connect a phone. `unauthorized` means you must accept the debugging prompt on the phone. Close the emulator window when finished. Automated tests stop their emulators, so start a separate one for manual use.

The debug APK uses a development signing key; it is separate from the signed release APK.

### Test locally

```sh
./scripts/test.sh
```

This runs release-script regression tests, debug/release compilation, JVM tests, lint, and native tests on API 29 and 36. Provide KVM and **disposable AVDs** named `quarantine-api29` and `quarantine-api36`, or set `TEST_AVD_API29` and `TEST_AVD_API36`. The runner clears Quarantine data and disables Accessibility services in these AVDs. It temporarily suppresses system error dialogs, restores that setting, and saves diagnostics; app crashes still fail the tests.

Reports are in `dist/local-tests/` and `app/build/reports/`. First-time dependency downloads need internet access. See the [manual device checklist](docs/device-testing.md) and [validation results](docs/validation.md) for coverage and remaining limitations.

### Reproduce a release

Requires Git, Docker with Linux amd64 support, network access for dependencies, and about 8 GB of available memory. Toolchain versions and checksums are pinned in [build-support](build-support/), [Gradle configuration](gradle/), and [mise.toml](mise.toml). Building the SDK container implies accepting the [Android SDK license](https://developer.android.com/studio/terms).

1. Check out the exact release tag in a clean checkout. Its commit should match `source_commit` in the release's `build-info.json`.
2. Download that release's APK, checksums, and build information. Save the APK as `dist/reference/quarantine.apk`.
3. Build and verify:

```sh
SOURCE_COMMIT="$(git rev-parse HEAD)" ./scripts/reproducible-build.sh "$PWD/dist/rebuild"
docker run --rm --platform linux/amd64 --entrypoint python \
  -v "$PWD:/src:ro" -v "$PWD/dist:/out" quarantine-build \
  /src/scripts/verify-apk.py /out/reference/quarantine.apk \
  /out/rebuild/quarantine-unsigned.apk /out/quarantine-reconstructed.apk
```

Verification copies only the reference APK's public signing data onto your rebuild, checks both signatures, and requires identical SHA-256 hashes. No private key is needed. This follows [F-Droid's reproducible-build approach](https://f-droid.org/docs/Reproducible_Builds/). To establish publisher identity, compare the signer certificate with a previously trusted release too.

For an independent unsigned comparison, build again to `dist/second` and compare the two `quarantine-unsigned.apk` files with `cmp`. When changing dependencies, review and update [Gradle verification metadata](gradle/verification-metadata.xml) and repeat reproducibility checks; do not disable checksum verification.
