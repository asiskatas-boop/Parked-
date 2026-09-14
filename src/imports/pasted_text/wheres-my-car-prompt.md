Design a minimal mobile app called “Where’s My Car?”

This is a very simple utility app for people whose cars connect to their phone via Bluetooth.

It is not for EV charging, smart cars, connected-car accounts, parking payments, reservations, or vehicle management.

There is:

No login
No account
No onboarding flow beyond required permissions
No social features
No cloud dashboard
No vehicle database
No EV features
No subscriptions
No unnecessary settings

The app has one job:

Remember where the user parked when their phone disconnects from the car’s Bluetooth, then help them walk back to the car.

Core concept

The user pairs/selects their existing car Bluetooth connection once.

When the phone disconnects from that Bluetooth device, the app automatically saves the phone's current location as the parked-car location.

The user does not need to manually press “I parked.”

Opening the app later should immediately answer:

Where is my car?

Product personality

Design this as a tool, not a lifestyle app.

It should feel:

Immediate
Lightweight
Reliable
Quiet
Functional
Native
Extremely easy to understand

Avoid:

Dashboard layouts
Large feature menus
Marketing language
Gamification
Profile avatars
Account UI
Car statistics
EV graphics
Fancy vehicle renders
Excessive cards

The interface should feel closer to Compass, Find My, Maps, or a system utility.

Main screen

Use a full-screen map.

The saved car location should be visually dominant.

Show:

Your car

with the distance clearly visible.

Example:

Your car
380 m away

Secondary information:

Parked 47 min ago

Then one primary action:

Directions

Tapping it should begin walking navigation or open the preferred maps app.

Keep everything else secondary.

Saved car marker

Create a custom car-location marker.

Do not use a generic red map pin.

Use:

Simple car glyph
Strong circular marker
Small shadow
Optional subtle pulse when first saved

The user's current location should remain a familiar blue location dot.

The user must instantly understand:

Blue dot = me
Car icon = my car
Bluetooth state

The app should quietly communicate which Bluetooth device is being used to detect parking.

Example small status text:

Connected to BMW Audio

or:

Watching: Golf Bluetooth

Do not make Bluetooth connection status the main visual focus.

When connected to the selected car:

Car connected

When disconnected and location is saved:

Parked location saved

First-time setup

Keep setup extremely short.

Screen 1:

Choose your car

Supporting text:

“Select the Bluetooth connection your car uses.”

Show available or previously connected Bluetooth devices in a clean native-style list.

Example:

BMW 320i
VW MEDIA
Ford Audio
MyCar

After selecting one, move directly to permission setup.

Permission screen

Only ask for permissions the app actually requires.

Explain them simply.

Headline:

Allow location access

Copy:

“We use your location when your car Bluetooth disconnects to remember where you parked.”

Primary action:

Allow Location

Do not use privacy-heavy marketing copy.

The app should feel transparent rather than defensive.

Home screen — connected state

While the phone is currently connected to the car Bluetooth, show the map and a minimal floating status panel.

Example:

Car connected

“We’ll remember this location when you disconnect.”

No large CTA is necessary.

Optionally provide one small manual action:

Save location now

This should be secondary and only exist as a fallback.

Home screen — parked state

After Bluetooth disconnects:

Show the car marker on the map.

Bottom floating panel:

Your car

380 m away

Parked 47 min ago

Primary button:

Directions

Secondary text action:

Update location

Small overflow menu:

Forget location
Change car Bluetooth
Settings
Close-to-car state

When the user gets very close to the saved location, simplify the message.

Example:

Your car is nearby

35 m

Do not clutter the map with excessive route information.

No saved location

If there is no parking location yet:

No parked location yet

“Connect to your car once and we'll remember where you leave it.”

If Bluetooth is currently connected:

Car connected

“Location will be saved when you disconnect.”

Incorrect automatic location

Because Bluetooth can sometimes disconnect before or after the user actually parks, provide a very easy correction mechanism.

Small secondary action:

Move car location

This opens manual map adjustment.

Use a centered car marker while the user moves the map.

Bottom button:

Set Car Here

Map controls

Keep only essential map controls:

Recenter on me
Recenter on car

Do not overload the map with controls.

Visual style

Use a highly restrained mobile visual system.

Direction:

White or very light neutral surfaces
Dark charcoal typography
One strong accent color
Native-looking map
Soft shadows
Rounded floating panels
Minimal borders

Suggested primary accent:

Deep blue or cobalt.

Avoid gradients.

Avoid oversized illustrations.

Avoid decorative elements with no functional purpose.

Typography

Use a modern system-like sans serif.

Prioritize large numbers.

Example hierarchy:

380 m
Large and bold

Your car
Medium-weight label

Parked 47 min ago
Small muted metadata

The most important information should be readable at a glance.

Layout philosophy

The user should be able to open the app and understand everything in under one second.

Use:

Full-screen map
One compact floating bottom panel
One primary action
Maximum 2–3 secondary actions visible at once

Do not create a traditional tab bar unless absolutely necessary.

Prefer a single-screen utility.

Settings

Keep settings extremely small.

Include only:

Car Bluetooth

Selected device

Location behavior

Automatic parking detection on/off

Navigation app

Apple Maps / Google Maps / System default

About

Privacy / app version

Nothing else unless required.

Dark mode

Create dark mode.

Use:

Dark map styling
Charcoal floating surfaces
White primary text
Muted gray metadata
Brightened blue accent

Maintain strong contrast.

Components to create

Create reusable Figma components for:

Car location marker
Current location marker
Bottom status panel
Primary button
Secondary button
Bluetooth device row
Permission sheet
Map control button
Toast message
Confirmation dialog
Settings row

Use Auto Layout and variants.

States to design

Create high-fidelity Figma frames for:

First launch
Bluetooth car selection
Location permission
Car Bluetooth connected
Car disconnected / parking saved
Finding parked car
User close to car
No saved location
Manual location correction
Bluetooth unavailable
Location unavailable
Settings
Dark mode connected state
Dark mode parked state
Interaction prototype

Prototype the core experience:

User selects car Bluetooth
Bluetooth shows connected
Bluetooth disconnects
Small confirmation appears:
Parked location saved
Car marker appears on the map
User later opens the app
App immediately shows distance to car
User taps Directions
Walking navigation starts

The entire product should feel like a tiny system utility whose purpose can be described in one sentence:

Your car disconnects. We remember where it is.