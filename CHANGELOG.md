# Changelog

## 0.1.4-alpha.1

- Show the full installed application version in Settings → About, including
  the preview suffix. The version is selectable for copying.
- Generate the displayed version and installer version from the same build value.
  No update checker or network requests are added.

## 0.1.3-alpha.1

- Stop brief gaps in motion reports from triggering “Release the cap to resume.”
  Movement stops while reports are missing and continues on fresh input without
  replaying accumulated movement. Held mouse buttons survive these gaps.
- Keep fresh-neutral rearming after pause, disconnects and mapping changes.

## 0.1.2-alpha.1

- Fix older frame timestamps being submitted after device-refresh or silence
  interruptions advanced the motion engine's clock.
- Keep the input thread and pause shortcut available after processing failures.
  Processing resets and pauses safely; resume and release the cap to center to
  continue without restarting the app.

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
