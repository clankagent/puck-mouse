# Changelog

## 0.1.1-alpha.1

- Link horizontal and vertical tuning independently for pointer and scrolling.
  Enabling a link copies the edited direction's tuning to its partner; movement
  assignments, reverse direction and on/off remain independent.
- Expand response exponent to 12 and pointer speed to 20,000 px/s; add a
  configurable full-speed point to control where the curve reaches its ceiling.
- New pointer defaults use 6,000 px/s, exponent 2.6 and full speed at 70% push.
  Existing profiles retain their settings. Apply the Precision + fast travel
  preset to opt into the new tuning without changing assignments.
- Preserve version-one settings, including values omitted by the old file format.
- Honor zero smoothing as an immediate response in the bundled motion core.

## 0.1.0-alpha.1

Initial Windows x64 preview with configurable pointer and scrolling mappings,
twist or tilt scrolling, per-output fine tuning, button actions, global pause
shortcut, held pause clutch, profiles and an isolated preview sandbox.

Includes a Java runtime and the pinned Puck 2.0.0-alpha.2 DLL. Automated checks
cover native processing, synthetic reports, settings, Windows registrations and
UI interactions. Physical SpaceMouse validation of this desktop app is pending;
only the measured `256f:c63a` Bluetooth report profile is enabled.
