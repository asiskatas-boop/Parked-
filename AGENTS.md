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
  - `ui/` — Compose screens (`App`, `HomeScreen`, `FuelScreen` (the Car tab), `SettingsScreen`)
    plus shared `Theme` and `Components`
- `app/src/main/res/values*/strings.xml` — all user-facing text. English is the
  default; Greek lives in `values-el`. Add every new string to both files.

## Product decisions to keep

- The parking confirmation is the only automatic alert after parking. No
  re-engagement or "log your fuel" nudges.
- Parking history is opt-in and off by default. Never collect location history
  silently.
- Refuels are the only fuel history. The fuel gauge (estimated level, range,
  "Correct") came back in 0.7 on the Car tab; the level keys are unchanged.
- No splash screen: opening the app is often the moment someone is looking for
  their car.
- Tabs: Home (where the car is), Car (the car, fuel level, service and ΚΤΕΟ,
  then refuels, odometer, colour), Settings (AutoPark, preferences, backup, about).
- The odometer only advances from GPS after the user has entered a real reading.
- Keep storage keys backward compatible; users update in place.
- The app is `com.parked.app` ("Parked!"). Builds are signed with the fixed CI
  key (SHA-256 a779dc23…); 0.3.1 and earlier used throwaway keys and can't be
  updated in place.

## Design tokens

Light only (since 0.7). Based on a warm, flat design system with the accent in
Parked green; Rivian-style status first, Revolut-style one big number per
screen. Colours and type live in `ui/Theme.kt`: page `Bg` #FFFBF5 (cream),
cards `Surface` #FFFFFF, `SurfaceSunk` #F3EEE6, borders `Border` #E7E5E4 and
`BorderStrong` #D6D3D1, text `TextPrimary` #1C1917, `TextMuted` #57534E
(7.3:1), `TextFaint` #A8A29E (decoration only), one accent `Green` #3F7A22
(white on it 5.5:1) with `GreenSoft` #EEF5E8 for "on" states, `Forest`, `Sand`,
and `Danger`, `Warn`/`WarnBg`, `Ok`/`OkBg` for status. No shadows: cards are
white with a 1 dp border. Radii: `RadiusSmall` 4 dp (buttons, chips, fields),
`RadiusCard` 12 dp (cards, sheets). Fonts: `HeadingFont` Literata (titles and
big numbers, has Greek) and `BodyFont` Open Sans, both variable and OFL in
`res/font/`. Icons: Phosphor regular (`res/drawable/ph_*.xml`, MIT, fill
variants for the active tab), reached through `Ph` in `ui/Components.kt`.
Touch targets are at least 48 dp; body text 15-17 sp with a line height of at
least 1.5.

Shared building blocks (green primary button, outline secondary button, text
links, `ParkedCard`/`SettingsCard`, rows with a chevron, `StatusChip`, pill
switch, fields, sheets, dialogs, `BigNumber`/`Overline`, `Motion` springs,
`Haptics`, `pressScale`) are in `ui/Components.kt`; the car drawing is in
`ui/CarArt.kt`. Reuse them rather than restyling per screen. Map tiles are
CARTO Voyager; keep the "© OpenStreetMap © CARTO" credit visible.
