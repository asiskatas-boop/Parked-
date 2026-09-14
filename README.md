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
This is an MVP scaffold. Before Play Store release, add:
- Proper app icon and branding
- Better permission education / denied-permission states
- Boot/restart handling if desired
- More robust Bluetooth profile handling for specific car systems
- Location accuracy / failure states
- Human-readable reverse-geocoded address
- Unit tests + instrumented tests
- Signed release build and Play App Signing
- Privacy policy and clear explanation of foreground location use
