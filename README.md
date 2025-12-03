# Android Status Reporter (Java)

## What it does
- Collects device metrics (memory %, temperature, battery level/voltage) and location (lat/lon/accuracy/provider/timestamp)
- Posts to Slack and/or your API. API calls can include an Authorization Bearer header
- Supports periodic sends (every 30s) or on-demand via Firebase Cloud Messaging (FCM) data messages
- Runs as a foreground service and can restart after boot

## Quick start (device)
- Install: use a published APK (Releases) or build `./gradlew clean assembleDebug` then `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Open the app and enter Slack webhook, API URL, optional API key
- Choose: enable/disable Slack, API, and periodic 30s sends (leave periodic off for FCM-only on-demand). FCM-triggered sends go to the API only
- Tap “Start Service” and grant notifications + location. Verify payloads arriving at Slack/API
- If sideloading via `adb`, enable USB debugging on the device (Developer Options → USB debugging) and accept the RSA prompt

## Backend setup for on-demand location
- Firebase: add Android app `android_status.app` to your Firebase project; place `app/google-services.json` locally (ignored by git). Download a service account JSON for FCM HTTP v1
- Token capture: on `onNewToken`, the app POSTs `{type: "registerToken", deviceId, fcmToken}` to your API endpoint (if configured). Store/update tokens per device on your backend. Without an API, you can read the token from `adb logcat -s FcmService`
- Trigger: when your backend wants a fix, send a data message `{ "type": "REQUEST_LOCATION" }` via FCM HTTP v1 to the stored token. The app immediately posts metrics/location to your API/Slack
- HTTP v1 example:
  ```bash
  curl -X POST \
    -H "Authorization: Bearer ACCESS_TOKEN" \
    -H "Content-Type: application/json UTF-8" \
    -d '{ "message": { "token": "DEVICE_FCM_TOKEN", "data": { "type": "REQUEST_LOCATION" } } }' \
    https://fcm.googleapis.com/v1/projects/PROJECT_ID/messages:send
  ```
- Helper script: `scripts/send_fcm.py --key service-account.json --project PROJECT_ID --token DEVICE_FCM_TOKEN` (requires `google-auth` and `requests`).

## End-to-end flow
1) App starts service → collects metrics/location
2) If periodic enabled: posts every 30s to enabled targets (Slack/API)
3) Token management: on token refresh, app POSTs `{type: registerToken, deviceId, fcmToken}` to your API (if set)
4) Backend stores token; when an event occurs (for example, video analytics), backend sends FCM `{type: REQUEST_LOCATION}` to that token
5) App receives FCM, triggers immediate `collectAndSend`, posting to your API

## API contracts
- Token registration request (app → backend)
  - Method: POST to your API URL
  - Body: `{"type":"registerToken","deviceId":"...","deviceName":"...","fcmToken":"..."}` (`deviceName` optional)
- Location payload (app → backend on periodic or on-demand)
  - Method: POST to your API URL
  - Body: `{"deviceId":"...","deviceName":"...","timestampMs":123,"memoryPct":34.8,"tempC":30.0,"batteryPct":99,"voltageV":4.27,"lat":-33.8637,"lon":151.2022,"accuracy":11.4,"provider":"fused"}`
- On backend: store/update tokens on every `registerToken`; mark tokens stale on FCM `NotRegistered` errors

## Permissions
- INTERNET
- FOREGROUND_SERVICE
- RECEIVE_BOOT_COMPLETED
- POST_NOTIFICATIONS (runtime on Android 13+)
- ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION
- WAKE_LOCK, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS

## Requirements (building yourself)
- Java 17 and Android SDK Platform-Tools (adb)
- Firebase `google-services.json` (for FCM) placed under `app/`
- Device on Android 8.1+ (minSdk 27) (tested on UniHertz Atom)

## Code overview
- `App`: UI to enter Slack/API/API key, enable or disable Slack/API/periodic, start or stop service
- `MetricService`: foreground service that collects metrics/location, posts to Slack/API; honors periodic/target toggles; can send immediately on FCM trigger
- `FcmService`: receives FCM data `{type: REQUEST_LOCATION}`, starts `MetricService` with `triggerImmediate`; on token refresh, posts token to API (if configured)
- `ApiPoster`/`SlackPoster`: lightweight OkHttp clients
- `Prefs`: encrypted/shared preferences for settings
- `MetricsCollector`: reads system stats and last known location (fused GPS/network)

## Notes
- Keep secrets out of git: `google-services.json`, service account keys, keystores stay local
- If you want only on-demand, disable periodic sends in the app UI
- FCM tokens can rotate; ensure backend stores the latest from `registerToken`
- Whitelist the app from aggressive battery savers/App Blocker on the device for reliable FCM and foreground service (for example, Settings → Battery → Battery optimization/App Blocker → allow this app)
- Quick test: start service, capture token (or let backend receive `registerToken`), send FCM REQUEST_LOCATION via script, confirm a single payload arrives at your API (and Slack if enabled)
