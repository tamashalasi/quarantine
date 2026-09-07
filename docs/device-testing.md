# Device acceptance checklist

Run on Android 10 (API 29), Android 13+ for notification permissions, and Android 16 (API 36). Repeat on a physical target phone because Accessibility/window behavior varies by OEM. Use at least two disposable target apps.

- Fresh install: no apps quarantined, 10-second hold, all filter; missing permissions/history show useful status rather than invented zero totals.
- Grant Usage Access and Accessibility access through onboarding. Grant or deny notifications. Confirm permission status refreshes after returning.
- Release the settings button at 9 seconds: resets to 10. Complete a continuous hold: settings open. Drag outside, rotate, open notification shade, press Home, or lock screen mid-hold: no unlock.
- Change duration to 1, 10, and 300 seconds. Empty, zero, negative, nonnumeric, and >300 input must not overwrite the last valid value. Verify persistence after killing/reopening Quarantine.
- Quarantine two apps via rows/checkboxes. Verify usage sorting and all three filters, icons/names/durations, and disabled essential apps. Compare summary totals with the listed apps' reported estimates.
- Open each target via launcher, Recents, and a deep link. An opaque unlock screen must intercept touches. Home must let the user leave without unlocking. Completing a hold must reveal the same target screen without relaunching its task.
- Unlock A: B stays blocked. Switch A → Home → B → A and lock/unlock the phone: A stays unlocked. Removing A from Recents does not revoke its explicit grant.
- Tap **Lock A in quarantine** while A is open: A is covered and Home appears; reopening A requires another hold. Tap while another app is open: that app remains usable. Old notification intents must not revoke a later new grant.
- Deny/revoke notification permission and disable the notification channel: **Lock unlocked apps** still revokes grants immediately. Restore permission/channel, reopen Quarantine, and confirm notifications return.
- Remove an app from quarantine: its grant/notification disappears. Add it again: it requires a new hold.
- Disable/re-enable Accessibility, kill Quarantine, and reboot: temporary grants clear, saved membership/duration survives, service status is accurate. Revoking Usage Access must not silently disable blocking.
- Test permission dialogs, keyboard, notification shade, calls, and lock screen without trapping essential system UI. Check large font, dark mode, landscape, and system navigation insets.
- Record split-screen, picture-in-picture, Recents previews, background media, and OEM battery-management limitations; do not describe them as comprehensive blocking.

Record device model, OS/API, build commit, results, and any observed failures. Never claim a scenario passed solely because its test code compiled.

## Local emulator preparation

Check `emulator -accel-check` before starting an AVD. The local KVM retry used `-accel on -cores 2 -memory 3072 -gpu swiftshader -feature -Vulkan -no-snapshot -timezone Etc/UTC`. The explicit timezone avoids invalid host timezone detection. A 480×800 display at density 160 reduced software rendering load.

Wait for boot completion, dismiss the lock screen, and confirm Android has no startup ANR dialogs before running instrumentation. `adb -s SERIAL shell dumpsys window` shows the focused window. A System UI ANR dialog can steal focus while the test activity is resumed; the hold button intentionally stays disabled when its window lacks focus. Keep emulator startup failures separate from application test failures, and retain the failed run's evidence when retrying.

Install both debug APKs, then run:

```sh
adb -s SERIAL shell am instrument -w \
  app.quarantine.android.test/androidx.test.runner.AndroidJUnitRunner
```

Run instrumentation before enabling Quarantine's Accessibility service, because the test runner's UI automation can suppress other Accessibility services. Then enable the service for the manual interception checks.

The local native runner temporarily sets `hide_error_dialogs=1` and sends `CLOSE_SYSTEM_DIALOGS`, following [Android CTS setup](https://android.googlesource.com/platform/cts/+/8c2a0a01038), so startup dialogs cannot capture test input. It restores the setting and saves logcat on exit. This applies only to disposable emulator instrumentation; perform the manual checklist with normal dialog behavior.
