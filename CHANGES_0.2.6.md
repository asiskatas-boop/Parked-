# Parked! 0.2.6 — quieter fuel + bug pass

## Fuel UX

- Fuel is now a current snapshot instead of a recurring logging task.
- "Correct level" updates the current tank level without creating a new history row.
- Refuels remain the intentional historical action and keep their own history.
- Added an At a glance card. After two full-tank refuels with odometer readings, Parked! can show average consumption and an approximate range based on the current gauge level.
- Trip cost remains a calculator only; it does not create or imply trip tracking.
- Existing fuel-level logs are preserved under "Past level checks" for upgrades, but new corrections no longer add more rows.
- Added a persisted "last updated" timestamp for the current fuel level.

## Bug / data fixes

- Fixed the odometer model so GPS distance is not exposed as a fake odometer when the user has never set a real odometer baseline.
- GPS distance accumulation now starts only after the odometer has been anchored by the user.
- Refuel odometer prefill therefore stays blank until there is a real odometer baseline instead of potentially showing distance-since-install as the vehicle odometer.
- Corrected tank-capacity helper copy; tank capacity is used for approximate litres/range, not the trip-cost formula.

## Retained UX decisions

- Parking completion stays clean: no fuel nag after parking.
- Parking confirmation remains the only automatic completion alert.
- No parking history is silently collected for a future Pro tier.
- No parking-difficulty heatmap is enabled without a trustworthy data source.
- Fixed Home live-location updates continuing after the Activity was paused; foreground map tracking now stops when the app leaves the foreground unless the user has explicitly enabled AutoPark's foreground service.
