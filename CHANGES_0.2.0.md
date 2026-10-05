# Parked! 0.2.0

Focused update; no P2/car-companion expansion.

## Changed
- Keeps the existing package ID (`com.parked.app`) and bumps Android versionCode from 1 to 2.
- Removes the automatic Location permission/settings prompt on normal app launch.
- AutoPark setup now continues through permission checks and the native Bluetooth enable prompt without requiring a second toggle.
- AutoPark parking capture prefers the last in-car/cached fused location before requesting a fresh balanced-power location.
- A saved car remains usable on Home even when a live phone location is unavailable.
- Parked notification now prompts **Log fuel** and opens the Fuel screen. There is no Refuel notification.
- Routine **Fuel level logs** are now separate from **Refuel** entries.
- Fuel level meter has E / 1/4 / 1/2 / 3/4 / F markers plus smaller notches.
- Fuel level logs and Refuel entries can be deleted with confirmation.
- Refuel is a manual form for litres, total paid, optional odometer, and full/partial tank.
- Refuel only advances the saved odometer when the entered reading is higher.
- Match My Car is simplified to a larger preview and common car colours.

## Data compatibility
Existing DataStore and SharedPreferences names/keys are retained. Existing `refuels` data is not rewritten or deleted. New routine fuel-level history uses the additive `fuelLevelLogs` key.

## APK update signing
Android also requires the new APK to be signed by the same certificate as the APK already installed. The repository's existing workflow builds a debug APK and does not contain a persistent signing key, so package/data compatibility alone cannot prove that a newly generated CI debug APK will be accepted as an in-place update.
