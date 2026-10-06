# UX audit pass

Implemented after the storytelling / retention / copy audit:

- Rewrote the two onboarding pages around the user outcome instead of BT/GPS technology.
- Added clearer onboarding progression cues ("Tap to see how it works" / "Tap to get started").
- Removed the automatic post-parking "Log your fuel!" prompt from Home.
- Removed the fuel CTA from the parking-saved notification so a completed parking action no longer creates extra homework.
- Reworded the parking confirmation state and notification around the saved outcome.
- Renamed the notification setting to "Parking alerts".
- Changed the fuel CTA from "Save level" to "Update level" and reframed the empty state as starting an estimate.
- Replaced the ambiguous car icon used for Settings navigation with the standard gear while keeping the compact icon-only bottom navigation.
- Corrected location/privacy copy in the web prototype so it no longer claims AutoPark is never active in the background.

Product decisions intentionally left for further discussion:

- No engagement notifications were added. Parked! should optimize for trust and passive utility rather than app-open frequency.
- No parking-difficulty heatmap was added until a trustworthy data source and confidence model are defined.
- No new fuel features were added; the next fuel iteration should reduce manual tracking rather than expand the feature set.
