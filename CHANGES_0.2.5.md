# Parked! 0.2.5 — UX consolidation

This pass brings the UX audit decisions together without adding another visible feature surface.

## Onboarding and brand story

- Added a short first-run brand animation on the dark-olive onboarding page.
- The animation uses the exact `ic_parked_logo.png` artwork as a rigid asset, so the P! mark and car silhouette cannot drift between generated frames.
- Background is the exact Parked! olive `#2B4A23`.
- Motion is a quick right-to-left entrance, hard brake with two damped whole-logo bounces, short hold, then exit left.
- The animation is muted, plays once, and falls back to a static logo when Android animations are disabled.
- Refined onboarding copy around the outcome first: Parked! remembers the spot; Bluetooth/AutoPark is explained only on the second page.

## Retention / notifications

- Kept the parking confirmation as the only automatic completion-loop alert.
- No generic re-engagement notification or fuel reminder was added after parking.
- Parking completion remains a clean end state rather than immediately creating another task.

## Navigation and copy

- Kept the compact three-item bottom navigation.
- Settings uses the conventional gear icon.
- Parking and fuel copy from the UX-audit pass remains outcome-led and less technical.

## Pro parking history direction

- Full parking history is the leading candidate for Parked! Pro because it adds passive value without asking users to log anything.
- This build does **not** silently collect a location-history archive. Historical parking should only begin after the feature is clearly presented and the user opts in / enables Pro.
- The current parking spot remains part of the core free experience.

## Product direction retained from the audit

- Free should solve the current parking problem cleanly.
- A future Pro tier can add passive value such as full parking history instead of forcing users into more manual tracking.
- No parking-difficulty heatmap is shipped until there is a trustworthy data source and confidence model.
- No new trip-distance feature was added. Parked! has no user-facing trip tracker; the existing internal distance accumulator remains an implementation detail for fuel/odometer logic.
