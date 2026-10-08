# Parked! — Android app

Parked! remembers where you parked. When the car's Bluetooth disconnects, a
foreground service saves the phone's location. The user can also save a spot by
hand, add a note or photo, set a parking timer, and track fuel and refuels.

The app is a single-module Android project written in Kotlin with Jetpack
Compose. The design source lives in Figma, not in this repository.

## Building

There is no Gradle wrapper. CI (`.github/workflows/main.yml`) builds with JDK 17
and Gradle 8.9:

```bash
gradle :app:assembleDebug
```

CI runs on pushes to `main` and to any `ci/**` branch. If a build fails, the
compiler errors are attached to the check run as a single annotation.

## Layout

- `app/src/main/java/com/parked/app/`
  - `MainActivity.kt` — entry point; handles widget/tile/notification actions
  - `data/` — `ParkingStore` (DataStore: spot, note, photo, timer, history),
    `FuelStore` (SharedPreferences: fuel level, refuels, odometer, service reminders)
  - `service/ParkingMonitorService.kt` — AutoPark foreground service
  - `receiver/` — boot restore and parking-timer alarms
  - `widget/`, `tile/` — home-screen widget and Quick Settings tile
  - `ui/` — Compose screens (`App`, `HomeScreen`, `FuelScreen`, `SettingsScreen`)
    plus shared `Theme` and `Components`
- `app/src/main/res/values*/strings.xml` — all user-facing text. English is the
  default; Greek lives in `values-el`. Add every new string to both files.

## Product decisions to keep

- The parking confirmation is the only automatic alert after parking. No
  re-engagement or "log your fuel" nudges.
- Parking history is opt-in and off by default. Never collect location history
  silently.
- Refuels are the only fuel history. The fuel level is a current snapshot that
  the user corrects only when the car's gauge differs.
- The odometer only advances from GPS after the user has entered a real reading.
- Keep storage keys backward compatible; users update in place.

## Design tokens

Colours and type live in `ui/Theme.kt`: olive `#2B4A23`, lime `#6EC436`, Inter.
Secondary text uses `TextSecondary` (meets WCAG AA on white). Shared building
blocks (buttons, sheets, rows, toasts) are in `ui/Components.kt`; reuse them
rather than restyling per screen.
