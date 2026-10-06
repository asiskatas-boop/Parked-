# Parked! 0.2.7 — deeper bug-check pass

## Fixed

- Fuel consumption insights now ignore full-tank refuels that have no odometer reading, preventing a blank optional odometer from becoming a false `0 km` anchor.
- Full-to-full average consumption now skips malformed/out-of-order anchor pairs and uses the widest valid recent span.
- Editing the odometer is treated as calibration rather than distance driven; an existing fuel-level anchor moves with the corrected odometer so a correction cannot appear to consume fuel.
- Refuels timestamped as "now" no longer accept an odometer meaningfully lower than the current reading. This prevents impossible newest-first history rows from corrupting consumption calculations.
- Custom bottom sheets (Refuel, Trip Cost, Odometer, Tank Capacity, Fuel Price) now close on the Android back gesture/button instead of backing out of the app.
- Settings is scrollable on shorter screens / larger text sizes, so the lower controls and car preview are no longer clipped.
- Settings status feedback is now an overlay rather than being laid out inside the lower preview area where it could be off-screen.
- "Get directions" now opens walking navigation to the parked car instead of driving navigation.
- Home rejects very poor live fixes and ignores stale cached locations older than five minutes, reducing false distance and "Your car is here" states.
- Returning from Location settings without enabling Location now cancels the one-shot AutoPark start request, preventing AutoPark from switching on unexpectedly during a later unrelated resume.

## Verification performed in this environment

- Kotlin source delimiter/string/comment structural checks pass for all compiled `.kt` sources.
- Android XML resources parse successfully.
- PNG resources validate successfully.
- Onboarding MP4 validates as H.264/yuv420p, 1080x1920, 4 seconds.
- Settings icon imports remain on the corrected `Icons.Filled.Settings` / `Icons.Outlined.Settings` path with `android.provider.Settings` aliased as `AndroidSettings`.
- Application ID remains `com.parked.app`.
- Version code is 9 and version name is 0.2.7.

## Still requires CI/device verification

A complete Android Gradle compile cannot run in this container because the Android SDK/Gradle toolchain is not installed. Run the existing GitHub Actions `:app:assembleDebug` workflow, then smoke-test AutoPark connect/disconnect, permission denial/re-enable, manual parking save, walking directions, parking alert delivery, refuel entry, fuel insights, and back behavior on the custom sheets.
