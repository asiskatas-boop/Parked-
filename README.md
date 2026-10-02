# Parked! — Android MVP

Parked! remembers where you parked by watching a selected paired Bluetooth device. When that device disconnects, a foreground service requests the current location and saves it. You can also save the current location manually.

## MVP features
- Pick a paired Bluetooth car/device
- Foreground monitoring service
- Auto-save location on Bluetooth ACL disconnect
- Manual “Save current location now”
- Map with parked-car marker using OpenStreetMap/osmdroid (no Google Maps API key)
- Open turn-by-turn navigation in Google Maps using an Android intent (no Maps API key)
- GitHub Actions build that uploads a debug APK artifact

## First run / setup
1. Finish the two intro screens. They are only shown once.
2. Open **Settings → BT Device** and choose the paired Bluetooth device used by your car.
3. Turn on **AutoPark**. Location and Bluetooth must both be enabled; Parked! keeps a foreground service running while AutoPark is on.
4. Turn on **Notifications** in Settings if you want a confirmation alert after a parking location is saved.

The Android APK is implemented in `app/` with Jetpack Compose. The root `src/` folder is a separate web/Figma prototype and is not used by the Android APK build.

## Build on GitHub
1. Create a new GitHub repository, e.g. `parked-android`.
2. Upload/push this project to the `main` branch.
3. Open **Actions → Build Android APK → Run workflow**.
4. When the workflow finishes, download **Parked-debug-apk** from the run's **Artifacts** section.
5. Unzip it and install `app-debug.apk` on your Android phone. You may need to allow installs from your browser/files app.

## Local build
Use JDK 17 and Gradle 8.9:

```bash
gradle :app:assembleDebug
```

APK output:
`app/build/outputs/apk/debug/app-debug.apk`

## Android behavior note
For reliable disconnect detection, Parked! runs a foreground service with a persistent notification while monitoring is enabled. This is intentional: modern Android restricts invisible long-running background work.

## Before production
Before Play Store release, still consider:
- Testing Bluetooth disconnect behavior against the specific car/head unit models you support (some cars keep an ACL connection alive after ignition-off)
- A signed release build and Play App Signing
- A privacy policy explaining foreground location and Bluetooth use
- Device/instrumented tests on Android 12 through Android 15+
- A production map-tile provider if usage grows beyond light personal/MVP traffic
- Human-readable reverse-geocoded address
- Unit tests + instrumented tests
