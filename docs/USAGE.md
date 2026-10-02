# Using Puck Mouse

This guide covers each part of the app in detail. For installation and first
steps, see the [README](../README.md).

## Status

The header shows the device and output state.

| Status | Meaning |
| --- | --- |
| **Paused** | The cap doesn't affect the pointer. Puck Mouse always starts in this state. |
| **Waiting for device** | Resumed, but no supported SpaceMouse is connected. |
| **Release the cap to resume** | Resumed. Output begins once the cap rests at center and no device button is held. |
| **Active** | Cap movement and device buttons control the pointer. |
| **Preview only** | Running the preview sandbox. Nothing is sent to Windows. |

The device indicator shows **SpaceMouse Wireless · Bluetooth** when a supported
device is connected. *Device needs a supported report profile* means Windows has a
SpaceMouse that Puck Mouse can't use yet, such as a different model or a USB
connection.

## Defaults

New profiles start with these settings:

| Output | Cap movement | Top speed | Dead zone | Curve | Full speed at | Smoothing |
| --- | --- | --- | --- | --- | --- | --- |
| Pointer · horizontal | Slide sideways | 6,000 px/s | 4% | 1.7 | full push | 0 ms |
| Pointer · vertical | Slide forward / back | 6,000 px/s | 4% | 1.7 | full push | 0 ms |
| Scroll · vertical | Twist | 12 notches/s | 14% | 1.7 | full push | 25 ms |
| Scroll · horizontal (off) | Tilt sideways | 10 notches/s | 14% | 1.7 | full push | 25 ms |

Button 1 is **Left click**, and Button 2 is **Toggle pause**. Pointer tuning is
linked; scroll tuning is unlinked. The pause shortcut is **Pause**.

## Mappings

Puck Mouse has four outputs: **Pointer · horizontal**, **Pointer · vertical**,
**Scroll · vertical** and **Scroll · horizontal**. Each one is driven by one of
the six cap movements:

- Slide sideways
- Slide forward / back
- Press / lift
- Tilt forward / back
- Tilt sideways
- Twist

For each output you can set:

- **Enabled** turns the output off without losing its settings.
- **Movement** sets which cap movement drives the output. Two outputs can use
  the same movement, and then both respond together.
- **Reverse direction** flips the output.
- **Top speed** and **Fine tuning**, described below.

**Reset to defaults…** restores all four mappings and both button actions in
the current profile, enables radial pointer response and links pointer tuning.
Scroll tuning is unlinked. The profile name is kept.

### Pointer direction

**Preserve pointer direction** applies the dead zone and curve to combined X/Y
push. The cap's direction determines the X/Y proportions, so a 30° push stays
30° with matching pointer tuning. Full speed is a total vector speed; diagonals
don't get faster or slower just because of their angle.

Pointer X/Y tuning starts linked. If you unlink and give the directions different
speeds or response settings, they become weighted differently and the resulting
angle can change. Movement assignment, direction reversal and on/off remain
independent. A disabled direction doesn't contribute to combined push.

Turn this option off to compare the older independent axis response. If both
pointer outputs use the same cap movement, independent response is used because
one source cannot provide two-dimensional direction. Scrolling always keeps its
independent response.

The direction preview compares mapped cap direction with target pointer
direction. It shows the target before smoothing, rather than claiming to measure
actual cursor travel. In radial mode, the curve's live marker uses combined push.

## Fine tuning

Every output has the same settings. The chart under **Fine tuning** shows how
speed rises as you push further. It also shows sample speeds and, when there's
input, the current push.

| Setting | Range | What it does |
| --- | --- | --- |
| **Top speed** | Pointer 50–20,000 px/s; scroll 1–60 notches/s | The fastest output, reached at the full-speed point. |
| **Dead zone** | 0–50% | Pushes smaller than this are ignored, so a resting hand doesn't drift. |
| **Full speed at** | At least 5 points above the dead zone (minimum 10%), up to 100% | How far you push to reach top speed. Pushing further adds nothing. |
| **Response curve** | 0.5–12 | 1 is a straight line. Higher values keep small pushes slow, and values below 1 react quickly to small pushes. |
| **Smoothing** | 0–150 ms | Evens out shaky motion. Higher values feel steadier but react a little later. 0 responds immediately. |

Between the dead zone and the full-speed point, speed follows the curve:
`top speed × progress ^ curve`, where progress runs from 0 at the dead zone to 1
at the full-speed point.

Raising the curve also slows medium pushes. To keep fast travel, pair a higher
curve with a lower **Full speed at** or a higher **Top speed**. The pointer
defaults use curve 1.7 with a 4% radial dead zone and full speed at full push.

**Restore default fine tuning** resets the dead zone, curve, full-speed point
and smoothing to the defaults for that output. Top speed is not changed.

### Presets

- **Natural pointer** applies to both pointer directions: 6,000 px/s, a 4%
  radial dead zone, curve 1.7, full speed at full push and no smoothing. It enables
  radial response and links pointer tuning, preserving assignments and direction.
- **Precision + fast travel** (pointer outputs) sets 6,000 px/s, an 8% dead
  zone, curve 2.6, full speed at 70% and 25 ms smoothing.
- **Gentle scrolling** (scroll outputs) sets a 14% dead zone, curve 1.7 and full
  speed at full push. Speed and smoothing are kept.

All presets keep the output's movement, direction and on/off setting. If tuning
is linked, a preset applies to both directions. The button shows a check mark
when the output already matches.

Upgrades retain custom tuning and scroll settings. The previous factory pointer
preset (6,000 px/s, 8%, curve 2.6, 70% push, 25 ms) adopts Natural pointer.
Radial response is enabled for older profiles; turn it off to compare the old
behavior. Use Natural pointer to retune a custom profile explicitly.

### Linked tuning

**Link pointer X/Y tuning** and **Link scroll X/Y tuning** work independently.
While a link is on, both directions share speed, dead zone, curve, full-speed
point and smoothing. Movement, reverse direction and on/off are still set
separately.

Turning a link on copies the selected output's tuning to its partner. Turning it
off keeps the current values, and you can then change each direction separately.

## Buttons

You can assign one of these actions to each SpaceMouse button:

| Action | Behavior |
| --- | --- |
| **Left click**, **Right click**, **Middle click** | Press to click, or keep it held while moving the cap to drag. |
| **Back**, **Forward** | Like the side buttons on a mouse, for example in browsers and File Explorer. |
| **Toggle pause** | Press to pause and press again to resume. |
| **Pause while held** | A clutch that stops output while you hold the button. When you let go, output continues once the cap settles at center. A clutch doesn't undo a pause you set some other way. |
| **Do nothing** | The button is ignored. |

Button 1 defaults to Left click, and Button 2 defaults to Toggle pause.

Clicks are only sent while output is active. Pausing, disconnecting or changing
a mapping releases any held click.

## Pausing and resuming

You can pause or resume in several ways:

- The **Resume / Pause** button in the header.
- The tray menu.
- The global pause shortcut, **Pause** by default.
- A device button set to **Toggle pause** or **Pause while held**.

After you resume, after a disconnect, or after you change a mapping or button
action, output waits until the cap rests at center and no device button is held.
This keeps the pointer from jumping when you resume while the cap is pushed.

If the device briefly stops sending reports, movement stops. It continues as soon
as new input arrives. You don't need to re-center, held clicks stay held, and
movement from the gap is not replayed.

If motion processing fails, Puck Mouse pauses, resets the processing and shows
an error. Choose **Resume** and release the cap to continue. If processing
still can't start, the error explains why.

### Pause shortcut

Change the shortcut in **Settings → Pause shortcut**. You can record a new key
combination or pick a suggested one. A shortcut uses Ctrl, Alt or Shift with a
letter, number, function key or Space. Pause and Scroll Lock also work on their
own. You can't use F12 or Windows key combinations.

If another app already uses the shortcut, Puck Mouse shows a message. If a
previous shortcut was working, it stays active until you choose one that's
available.

Upgrades change the old default **Ctrl + Alt + P** to **Pause**. Other saved
shortcuts are preserved, including their modifiers.

## Preview sandbox

Choose **Try without device** (or **Preview** in the header) to test mappings
without a SpaceMouse. The sliders under **Live input** simulate cap movement, and
the output panel shows the rates that would be sent. Puck Mouse never moves the
pointer, scrolls or clicks in other apps while preview is on. When you leave
preview, Puck Mouse returns to paused.

## Profiles

A profile holds the four mappings, both button actions and the link settings.
You can switch profiles from the header or the **Profiles** page. You can
rename, duplicate, reset or delete a profile, and you can keep up to 32 of them.
Resetting restores radial pointer response and linked pointer tuning, with
scroll tuning unlinked.

- **Export all…** saves every profile to a JSON file. It also saves your
  preferences, such as the pause shortcut and tray setting.
- **Import…** adds the profiles from a file alongside your existing ones and
  selects the first imported profile. Your existing profiles and preferences stay
  as they are, and preferences stored in the file are ignored.

## Window, tray and startup

By default, closing the window hides Puck Mouse in the notification area so
the pause shortcut keeps working. Use the tray icon to reopen the window, pause
or resume, or **Quit**. You can turn this behavior off under **Settings → Window**.

Only one copy of Puck Mouse runs at a time. If you start it again, a message tells
you to open the running copy from the tray.

Puck Mouse never registers itself to start with Windows. If you want it to start
automatically, add a shortcut to your Startup folder. It will still start paused.

## Settings file

Settings are saved automatically to `%APPDATA%\PuckMouse\settings.json`. Nothing
is sent anywhere, and Puck Mouse has no account, telemetry or network features.

If the file can't be read, Puck Mouse starts with defaults and shows an error.
Your original file isn't overwritten. The next time settings are saved, it's kept
as `settings.invalid-<timestamp>.json` in the same folder.

## Troubleshooting

**The device isn't detected.** Make sure the SpaceMouse Wireless is paired over
Bluetooth in Windows settings. USB connections and other models aren't supported.

**Nothing moves after resuming.** Let go of the cap and any device buttons. Output
starts once the cap rests at center.

**The pointer doesn't move in one particular window.** That app may be running as
administrator. Windows doesn't let Puck Mouse, which runs without administrator
rights, control those apps. Puck Mouse pauses and shows an error when Windows
reports an output failure. Windows doesn't identify privilege blocking as the
cause; see Microsoft's [SendInput documentation](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-sendinput).

**The shortcut doesn't work.** Another app may already use it. Check
**Settings → Pause shortcut** and choose a different shortcut.

**A movement goes the wrong way.** Turn on **Reverse direction** for that output.
Axis directions haven't been formally validated on hardware.

**The 3Dconnexion driver is installed too.** Puck Mouse doesn't disable it or
change Windows pointer settings. Using both together hasn't been verified.
