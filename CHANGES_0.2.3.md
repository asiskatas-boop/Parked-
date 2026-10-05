# Parked! 0.2.3

Car-companion usability pass.

- Added a dedicated Trip Cost calculator on the Fuel screen.
  - Calculates from distance, L/100 km, and fuel price.
  - Supports round trips.
  - Pre-fills learned consumption from refuel history when available.
  - Pre-fills the latest known fuel price when available.
  - Does not create or modify Refuel entries or Fuel Logs.
- Kept Refuel and Fuel Logs explicitly separate in the UI.
  - Refuel remains the dedicated fill-up action and has its own history.
  - Fuel Logs contain only saved gauge/fuel-level readings.
- Fuel Log deletion is now one tap with an Undo snackbar.
- Added lightweight save feedback for fuel-level saves and refuels.
- Added numeric/decimal keyboards to fuel, refuel, odometer, tank-capacity, and calculator inputs.
- Added the last-saved timestamp under the fuel gauge.
- Renamed BT Device to Car Bluetooth.
- Renamed Match my car to Car appearance and made the car preview tappable.
- Split Tank capacity out of the Odometer editor into its own setting.
- Simplified AutoPark prompts to short action-only messages.
- Bumped Android build to version code 5 / version 0.2.3.

Build fix: corrected invalid Compose padding(horizontal + top) overload in Fuel screen.
