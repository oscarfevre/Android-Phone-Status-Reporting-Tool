# Android Status Slack Reporter (Java)

This is a minimal Android (Java) app that collects basic device metrics (memory, temperature, battery) plus location/timestamp. It posts formatted status messages to a Slack Incoming Webhook and can also POST a JSON payload (including GPS) to any API endpoint you configure. API calls can include an API key header for authentication.

Overview

- Foreground service (`MetricService`) periodically reads metrics and posts to Slack and/or your API.
- Location is requested via Google’s FusedLocationProvider with a GPS/network fallback so you get a fix even on devices without Play Services.
- Simple UI (`App` activity) to enter the Incoming Webhook URL, an API endpoint, and an optional API key, then start/stop the service.
- Boot receiver (`BootReceiver`) can auto-start the service after device boot if a webhook URL is stored.

Getting the app

- Download a published APK from the repo’s Releases and install with `adb install -r app-release.apk` (enable installs from unknown sources).
- Or build it yourself (JDK 17 + Android SDK Platform-Tools): `./gradlew clean assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Set up on the device

1. Open “Android Status”.
2. Paste your Slack Incoming Webhook URL, API endpoint URL, and (optional) API key.
3. Tap “Start Service”; grant notification + location permissions.
4. Verify Slack/API payloads are arriving.

Permissions

- INTERNET: to post to Slack.
- FOREGROUND_SERVICE: to run a persistent background service on Android 8.1+.
- RECEIVE_BOOT_COMPLETED: optional, to restart service after boot.
- POST_NOTIFICATIONS: for showing the foreground notification on Android 13+ (requested at runtime).
- ACCESS_COARSE_LOCATION and ACCESS_FINE_LOCATION: to capture location for the API payload.

Location behavior

- The app asks FusedLocationProviderClient for high-accuracy updates. It will use GPS when available and fall back to Wi‑Fi/cell; if fused isn’t available, it falls back to GPS+network via `LocationManager`.
- If you want only GPS fixes, filter payloads where `provider == "gps"`. Otherwise `provider` may be `fused` (best available) or `network` (coarser).
- `accuracy` in payloads is meters at ~68% confidence (~1σ). Lower numbers mean a tighter estimated error circle.

How it works

- The Activity (`App`) lets you paste a Slack Incoming Webhook URL, an API endpoint URL, and an API key, saves them in encrypted SharedPreferences, and starts/stops the foreground service.
- The foreground service (`MetricService`) posts a message every 30s with memory %, temperature (thermal zones/hwmon/battery fallback), battery level, and voltage to Slack. It also sends JSON to your API with metrics plus `lat`, `lon`, `accuracy`, `provider`, and `timestampMs`, and includes an Authorization Bearer header if an API key is provided.
- `BootReceiver` listens for `BOOT_COMPLETED` and restarts the service if a webhook is saved.
- `SlackPoster` uses OkHttp to POST `{ "text": "<message>" }` directly to Slack so no server is involved.
- `ApiPoster` uses OkHttp to POST JSON to your API endpoint (e.g., `{ deviceId, timestampMs, memoryPct, tempC, batteryPct, voltageV, lat, lon, accuracy, provider }`).

Requirements (if building yourself)

- Java 17, Android SDK Platform-Tools (adb) on your PATH.
- Slack Incoming Webhook URL, API endpoint URL (use `http://10.0.2.2:<port>/...` to hit a host service from an emulator), optional API key.
- Device on Android 8.1+ (minSdk 27). Android Studio is optional; CLI is fine.

License

Apache 2.0 (see LICENSE).
