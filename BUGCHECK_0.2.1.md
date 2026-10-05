# Parked! 0.2.1 bug-check pass

This pass was deliberately limited to reliability and the agreed Parked! scope.

## Fixed in this pass

- AutoPark recovery no longer leaves the persisted switch ON when the monitor cannot be recovered because the selected device, Bluetooth permission, Location permission, or Location service is unavailable.
- AutoPark setup now orders permission -> Bluetooth enable -> device selection -> Location service -> monitor start, removing the no-device/no-permission dead end.
- The BT Device row uses the native Bluetooth enable dialog as well.
- Saved parking locations remain usable even when live Location permission is unavailable; permission is only needed to save/locate the phone live.
- Notification preference checks the Parking alerts channel, not only the app-wide notification switch, and opens the channel settings when that channel is blocked.
- Odometer GPS accumulation filters inaccurate fixes, stationary wander, impossible jumps, NaN/Infinity input, and duplicate distance updates.
- Duplicate Bluetooth disconnect broadcasts are guarded so one parking event is not saved twice; a completed reconnect clears the duplicate-event window.
- Parking capture falls back to a recent in-car fix and fused last-known location before requesting a new balanced-power fix.
- Fuel/refuel numeric inputs reject NaN/Infinity and invalid negative values.
- Version-1 fuel-meter history is migrated once into Fuel Level Logs. The exact old JSON is backed up internally before the old bucket is cleared.
- The legacy migration is synchronized and committed as one transaction so the Activity and foreground service cannot race it and overwrite the backup.
- A pre-existing `fuelLevelLogs` key prevents genuine 0.2.x Refuel entries from being reclassified after logs were deleted.

## Integrity checks performed

- Application ID remains `com.parked.app`.
- Version code is 3; version name is 0.2.1.
- AndroidManifest.xml parses successfully.
- All referenced drawable and font resources exist.
- Kotlin parser pass found no structural syntax markers such as missing braces/tokens. Full Android compilation was not possible in this environment because the Android SDK/Gradle Android toolchain is not installed.

## Update-signing note

Data storage is update-compatible. Android still requires the new APK to be signed with the same signing certificate as the APK already installed. The existing repository workflow creates a debug APK and does not include a persistent signing key, so signature compatibility with an older GitHub Actions debug APK cannot be guaranteed from source alone.
