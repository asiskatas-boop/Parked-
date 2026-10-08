# Parked!

An Android app that remembers where you parked.

- **AutoPark** saves the spot when your car's Bluetooth disconnects. It comes back
  on its own after a reboot or an app update.
- **Save by hand** from the app, the home-screen widget or the Quick Settings tile.
  Saving over an earlier spot can be undone.
- **Find the car**: map with your position and the car, walking distance and time,
  and walking directions in Google Maps (or any maps app).
- **Spot note and photo**, for example "Level −2, B14".
- **Parking timer** with a reminder 10 minutes before the end and one at the end.
- **Optional parking history** of your last 50 spots, off by default and kept on the
  phone only.
- **Fuel**: current level with an estimate that drops as you drive, refuel history,
  average consumption and range, monthly spending, trip cost, and CSV export.
- **Service reminders** by kilometres, months, or both.
- English and Greek.

Maps use OpenStreetMap through osmdroid, so no Google Maps API key is needed.

## Install a build

1. Open **Actions → Build Android APK** on GitHub and pick the latest green run on
   `main`.
2. Download **Parked-debug-apk** from the run's **Artifacts**, unzip it and install
   `app-debug.apk` on your phone.

### Updating without losing data

Android only installs an update over an existing app if both are signed with the same
key. Add a fixed debug key to the repository once:

1. In GitHub, go to **Settings → Secrets and variables → Actions → New repository
   secret**.
2. Name it `DEBUG_KEYSTORE_BASE64` and paste the base64 text of a debug keystore
   (`base64 -w0 debug.keystore`).

Builds made before the secret existed each had a random key, so uninstall the old app
once before installing the first build made with the secret. After that, every new
build installs over the previous one.

## First run

1. Finish the two intro screens.
2. In **Settings**, choose your car under **Car Bluetooth** and turn on **AutoPark**.
3. Optionally set **Odometer** and **Tank capacity** for fuel estimates and kilometre
   service reminders.

## Build locally

JDK 17 and Gradle 8.9:

```bash
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## How AutoPark works on Android

AutoPark runs a foreground service with a persistent notification while it is on.
Android only lets it read location if the service was started while the app was on
screen. After a reboot it therefore starts in a limited mode: it still notices the
car disconnecting, and sends a "tap to save" notification instead of saving silently.
Opening Parked! once makes it fully automatic again.

## Before a Play Store release

- Test Bluetooth disconnect behaviour with the head units you support; some keep a
  connection alive after the ignition is off.
- A signed release build and Play App Signing.
- A privacy policy covering foreground location, Bluetooth, the camera (spot photo)
  and exact alarms (parking timer).
- Device tests on Android 12 to 15+.
- A production tile provider if map traffic grows beyond light personal use.
