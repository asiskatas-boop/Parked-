# Parked! 0.2.4

Reliability pass after the 0.2.3 companion update.

- Fixed the Compose padding compile failure carried over from 0.2.3.
- Fixed partial refuels overestimating fuel level when the entered odometer is ahead of Parked's tracked odometer.
- Fuel range estimation now refuses to calculate from a missing/zero tank capacity instead of collapsing the estimated level toward 0%.
- The Fuel gauge no longer assumes a fake 50 L tank when tank capacity is unknown; it asks for tank capacity instead.
- Notification preference now survives the trip into Android channel settings, so enabling Parking alerts there does not require toggling it a second time in Parked.
- Refuel, Trip Cost, odometer, tank-capacity, and fuel-price sheets now account for the on-screen keyboard and can scroll instead of hiding their Save/Done controls.
- Refuel, Fuel Logs, and Trip Cost remain separate features and data flows.
