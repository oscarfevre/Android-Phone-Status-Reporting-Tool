# Android Status Slack Reporter (Java)

This is a minimal Android (Java) app that collects basic device metrics (memory, temperature, battery) and posts formatted status messages to a Slack Incoming Webhook.

Overview

- Foreground service (`MetricService`) periodically reads metrics and posts to Slack.
- Simple UI (`App` activity) to enter the Incoming Webhook URL and start/stop the service.
- Boot receiver (`BootReceiver`) can auto-start the service after device boot if a webhook URL is stored.

Notes

- You can build from the command line with Gradle and adb (Android Studio is optional).
- Minimum SDK is set to 27 (Android 8.1) and should run on Android 8.1+ devices like the UniHertz Atom, though some `/sys` files may be restricted on some devices.
- Keep the webhook URL private. Store it in encrypted preferences for production.

Permissions

- INTERNET: to post to Slack.
- FOREGROUND_SERVICE: to run a persistent background service on Android 8.1+.
- RECEIVE_BOOT_COMPLETED: optional, to restart service after boot.
- POST_NOTIFICATIONS: for showing the foreground notification on Android 13+ (requested at runtime).

How it works

- The Activity (`App`) lets you paste a Slack Incoming Webhook URL, saves it in SharedPreferences, and starts/stops the foreground service.
- The foreground service (`MetricService`) posts a message every 30s with memory %, temperature (thermal zones/hwmon/battery fallback), battery level, and voltage.
- `BootReceiver` listens for `BOOT_COMPLETED` and restarts the service if a webhook is saved.
- `SlackPoster` uses OkHttp to POST `{ "text": "<message>" }` directly to Slack so no server is involved.

Requirements

- Java 17 (for Gradle builds) — install a JDK if you want to build yourself.
- Android SDK Platform-Tools (adb) on your PATH.
- A valid Slack Incoming Webhook URL.
- A device running Android 8.1+ (tested on UniHertz Atom).
- Android Studio (optional) if you prefer a GUI — otherwise use the CLI commands below.

Build, install, and connect to Slack (CLI)

1. Connect the device with USB debugging enabled and accept the RSA prompt.
2. Check device is recognised with `adb devices`
3. Build the APK (requires JDK 17): `./gradlew clean assembleDebug`
4. Install to the device: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
5. Open "Android Status" on the phone.
6. Paste your Slack Incoming Webhook URL into the text field.
7. Tap "Start Service" (grant notification permission on Android 13+). A foreground notification should appear.
8. Check your Slack channel for messages like: `[time] [timestamp] NOTIFICATION [Camera <id>] MEM: X% | temp=Y'C | Battery: Z% | Voltage: V`.
9. To stop, tap "Stop Service" in the app. If the device reboots and a webhook was saved, the boot receiver restarts the service automatically.

If you don’t have Java installed, you can’t build with Gradle. In that case, you would need a prebuilt APK from a trusted source and install it with `adb install`. For security, building your own APK from this source with a JDK is recommended.

License

Apache 2.0 (see LICENSE).
