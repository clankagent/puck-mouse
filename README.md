# Puck Mouse

Use a 3Dconnexion SpaceMouse as an everyday mouse on Windows. Slide the cap to
move the pointer, twist it to scroll, and press a device button to click. Puck
Mouse controls the regular Windows pointer and scroll wheel, so it works in
ordinary desktop apps, including ones without SpaceMouse support. It can't
control apps that run as administrator.

Each movement has its own tuning, so small pushes can stay precise while firmer
pushes still cross the screen quickly.

**[Download Puck Mouse](https://github.com/clankagent/puck-mouse/releases)**
(MSI or EXE installer for Windows x64). Alpha builds are listed as pre-releases.

> [!IMPORTANT]
> **Alpha, one supported device.** Puck Mouse only works with a SpaceMouse
> Wireless connected over Bluetooth that identifies as `256f:c63a`. Other
> models, USB connections and other report formats aren't supported yet.
> Hardware validation is incomplete. Comfort, latency and use alongside the
> 3Dconnexion driver haven't been verified.

![Puck Mouse showing the Mappings page, with simulated cap movement in the preview sandbox](docs/screenshots/preview.png)

## Install

You need 64-bit Windows, the SpaceMouse paired in Windows Bluetooth settings,
and the [Microsoft Visual C++ Redistributable (x64)](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist).
Java is bundled with the installer.

Download the MSI or EXE from [Releases](https://github.com/clankagent/puck-mouse/releases).
Both install the same app. Alpha installers aren't code-signed, so Windows may
warn about an unknown publisher. Each release includes SHA-256 checksums so you
can verify the files.

## First use

1. Start **Puck Mouse** from the Start menu. The header should show
   **SpaceMouse Wireless · Bluetooth**. If it shows *Device needs a supported
   report profile*, Windows found a SpaceMouse that Puck Mouse can't use.
2. Puck Mouse always starts **paused**. Click **Resume** or press
   **Pause**.
3. Let go of the cap. Output begins once the cap rests at center, and the status
   changes from *Release the cap to resume* to *Active*.

If a movement goes the wrong way, select that output on the **Mappings** page
and turn on **Reverse direction**. To experiment without a device, choose
**Try without device**. The preview never sends input to Windows.

## Default controls

| Input | Default action |
| --- | --- |
| Slide sideways | Move the pointer left and right |
| Slide forward / back | Move the pointer up and down |
| Twist | Scroll vertically |
| Tilt sideways | Scroll horizontally (off until you turn it on) |
| Button 1 | Left click, or hold to drag |
| Button 2 | Pause and resume |
| Pause key | Pause and resume from any app |

The pointer responds to the direction you push, with a circular dead zone and
a speed curve based on combined push. It reaches top speed at full push.
Scrolling has a larger dead zone and a gentler curve. The
[usage guide](docs/USAGE.md#defaults) has the exact values.

You can assign any of the six cap movements to any output. To scroll by tilting
instead of twisting, select **Scroll · vertical** and set **Movement** to
*Tilt forward / back*. A button can also be a right or middle click, Back,
Forward, or a pause clutch.

## Pausing

The shortcut works in any app, even while the window is hidden. You can also
pause from the header, the tray menu or Button 2. If you set a button to
**Pause while held**, it works as a clutch: output stops while you hold it, so
you can reposition your hand.

A pause, a disconnect or a mapping change releases held clicks, and output then
waits until the cap is back at center. A brief gap in device reports only stops
movement until new input arrives. Held buttons stay held, and the missed
movement isn't replayed.

## Adjusting the feel

Select an output on the **Mappings** page. **Top speed** is always visible.
**Fine tuning** holds the other settings, along with a chart of the response.

| If… | Try… |
| --- | --- |
| The pointer creeps while your hand rests on the cap | Raise **Dead zone** |
| Small targets are hard to hit | Raise **Response curve** |
| Crossing the screen takes too long | Raise **Top speed**, or lower **Full speed at** |
| Motion looks jittery | Raise **Smoothing** |
| Motion feels late | Lower **Smoothing** |
| Scrolling is too fast or too sensitive | Lower **Top speed**, or apply **Gentle scrolling** |

To tune both directions together, turn on **Link pointer X/Y tuning** or
**Link scroll X/Y tuning**. Movement, direction and on/off stay separate.
Use **Natural pointer** for the current pointer defaults. **Preserve pointer
direction** switches between radial and independent axis response for comparison.
Upgrades keep custom tuning; the previous factory pointer preset adopts the new
shape. Scroll tuning stays as saved, with **Gentle scrolling** available as a preset.

## Settings, privacy and updates

Settings save automatically to `%APPDATA%\PuckMouse\settings.json`, and profiles
can be exported and imported. Puck Mouse has no account, telemetry, network
features or cloud sync, and it never registers itself to start with Windows.
Closing the window keeps it in the tray so the shortcut keeps working. To exit,
choose **Quit** from the tray icon.

Puck Mouse doesn't check for updates. **Settings → About** shows the installed
version. To update, quit Puck Mouse and run a newer installer from
[Releases](https://github.com/clankagent/puck-mouse/releases). Your settings are
kept.

## Development and license

Puck Mouse is built with Kotlin and Compose Desktop, and the bundled
[Puck](https://github.com/clankagent/puck) library handles motion processing.
For building, testing and validation scope, see
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). Release notes are in
[CHANGELOG.md](CHANGELOG.md).

The project is licensed under [MIT](LICENSE). The bundled Puck library has its
own license and notice. SpaceMouse is a trademark of 3Dconnexion, and Puck Mouse
is an independent project.
