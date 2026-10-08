# Changelog

## 0.3.2

- Every build is now signed with the same key, so updates install over the
  previous version (one last uninstall needed after 0.3.1).
- UX audit fixes: readable header text, "Set up AutoPark" at the end of the
  intro and from the map tip (which can now be dismissed), faster splash after the
  first launch, a quieter "Save here" that asks before replacing a far-away spot,
  fuel level saved on release with Undo, odometer required for full fills, photo
  error state, portrait only, labelled tabs, chips that show what's saved, and one
  type scale and page-title style across the app.

## 0.3.1

- Video splash on launch (tap to skip); it was hidden behind the window before.
- Map fills only the visible area, so the car marker shows without zooming; one
  map button centres on the car, or fits you and the car when you're away.
- A banner on the map explains when location permission, Location or AutoPark is
  off, with a button to fix it.
- Fixed a crash when leaving Home while the map was updating.
- Service reminders: the Add button was missing on an empty list.
- Refuels take the amount paid and price per litre; litres are calculated.
  "Filled the tank" is off by default.
- Fuel gauge marks line up with the handle; "Set tank size" replaces a dash.
- Car preview redrawn as sharp vector art in every colour.
- Readable text on the second intro page; equal-size Save and Share buttons;
  no grey bar under the tabs.
- Smoother transitions between tabs and pages.
- The last crash is kept on the phone and can be shared from Settings.

## 0.3.0

### New
- Spot note and photo, shown on Home and included when sharing the spot.
- Parking timer with a heads-up 10 minutes before the end and an alert at the end.
  Survives reboots.
- Home-screen widget (Save spot / Find car) and a Quick Settings tile.
- Map buttons to centre on the car and to show both you and the car; walking
  distance and time on Home.
- Service reminders by kilometres and/or months, with one alert when due.
- Monthly fuel spending chart and CSV export of refuels.
- Optional parking history (last 50 spots), off by default.
- Greek translation; all text moved to string resources.

### Fixed
- AutoPark no longer stops silently. It restarts after a reboot or update, falls back
  to a "tap to save" alert when Android blocks background location, and notifies you
  if it cannot run at all.
- AutoPark now tracks the drive when it starts while the car is already connected.
- Saving a spot over an existing one can be undone, and a successful save is confirmed.
- Fuel range and gauge now drop with the distance driven since the level was set.
- The refuel odometer is no longer pre-filled with a GPS estimate that could be saved
  as a real reading; the estimate is shown as a hint.
- Forms are real bottom sheets: they cover the tab bar, close on back and outside tap,
  and stay above the keyboard.
- Home no longer flashes the empty state while the saved spot loads.
- The odometer shows the exact reading instead of "83.5k km".
- CI uses a fixed debug signing key (once the secret is added) so updates install over
  the previous build.

### Design and accessibility
- Secondary text darkened from 3.2:1 to 5.1:1 contrast (WCAG AA).
- Focused fields, switches and the slider use olive instead of lime for contrast.
- Buttons grow with large font sizes instead of clipping; whole settings rows toggle.
- Bottom tabs announce their name and selected state; headings are marked for
  screen readers; status uses icons and words, not colour alone.
- One snackbar style for all confirmations, replacing three different toasts.
- Consistent sentence-case labels; one "Get directions" action.

### Removed
- The Figma Make web prototype exported into this repository (the Figma file itself
  is unaffected), a broken backup of the main screen, unused images and roughly
  200 lines of unused code.

## 0.2.x

- 0.2.7 — Consumption ignores refuels without odometer readings; odometer edits are
  calibrations; back closes sheets; scrollable Settings; walking directions.
- 0.2.6 — Fuel level became a single snapshot; GPS distance only counts after a real
  odometer baseline; live location stops when the app is in the background.
- 0.2.5 — Onboarding animation and outcome-led copy; parking confirmation is the only
  automatic alert.
- 0.2.4 — Partial-refuel estimate fixes; sheets account for the keyboard.
- 0.2.3 — Trip cost calculator; undo for deleting level checks.
- 0.2.2 — Refuel button; slider gauge with ¼ ½ ¾ markers; car colour fixes.
- 0.2.1 — AutoPark recovery and setup order; duplicate disconnect guard; v1 fuel
  history migration.
- 0.2.0 — Fuel level logs separated from refuels; manual refuel form.
