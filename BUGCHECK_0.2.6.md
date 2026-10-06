# Parked! 0.2.6 bug-check pass

## Fixed in this pass

- Home live-location updates now stop when the Activity is paused. Background location work is left to the explicit AutoPark foreground service instead of the map screen continuing to request fixes after the app is backgrounded.
- The odometer no longer exposes GPS distance-since-install as if it were the vehicle's real odometer when no baseline has been entered.
- GPS odometer accumulation now starts only after the user has set a real odometer baseline.
- Refuel odometer prefill stays blank until that baseline exists, preventing values such as `42 km` from appearing for a car whose real odometer was never configured.
- New fuel-level corrections update one current snapshot instead of silently creating an ever-growing manual log.
- New installs no longer imply the tank is known to be 100% full; the first Fuel visit begins as an unset level and asks the user to set it once.
- Fuel "last updated" state is now persisted independently from old history rows and is backfilled conservatively for upgrades.
- Tank-capacity helper copy now matches the actual calculations.

## Regression / integrity checks

- Kotlin structural parser pass found no syntax markers such as `expecting`, `unexpected tokens`, unclosed constructs, or brace/parenthesis imbalance.
- MainActivity.kt, FuelStore.kt, ParkingStore.kt, and ParkingMonitorService.kt all have balanced structural delimiters after the edits.
- AndroidManifest.xml and XML resources parse successfully.
- All PNG resources open successfully.
- The onboarding animation is a valid H.264/yuv420p MP4, 1080x1920, 4.0 seconds.
- The React/TypeScript prototype has no TypeScript syntax diagnostics in the static parse pass; the remaining diagnostics are unresolved React/Leaflet modules because dependencies are not installed in this environment.
- Application ID remains `com.parked.app`.
- Version code is 8; version name is 0.2.6.
- Existing fuel/refuel history storage keys remain compatible with prior 0.2.x builds.
- Existing parking storage remains unchanged; no parking-history archive is collected silently.

## Build limitation

A full Android Gradle compile could not be run in this container because the Android SDK and Gradle executable are not installed. The repository's GitHub Actions workflow is configured to build `:app:assembleDebug` with Java 17 and Gradle 8.9, so the final compile should still be allowed to run there before distributing the APK.

## Release note

This is ready for a beta/release-candidate build from a product-code perspective, but a store/public release should still include one real-device smoke test of AutoPark connect/disconnect, permission denial/re-enable, manual Save, notification delivery, and Fuel/Refuel flows, plus your normal signing/privacy/store-listing checks.
