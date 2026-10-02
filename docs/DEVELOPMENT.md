# Developing Puck Mouse

Puck Mouse is a Kotlin/JVM desktop app built with Compose Desktop. It runs on
Windows x64 only. Motion processing comes from the bundled
[Puck](https://github.com/clankagent/puck) DLL through its C ABI.

## Requirements

- Windows x64.
- JDK 21. The build uses the checked-in Gradle wrapper and a Java 21 toolchain.
- The Microsoft x64 Visual C++ v14 runtime, which the Puck DLL needs.

## Build, test and run

```powershell
./gradlew.bat test
./gradlew.bat run
./gradlew.bat createDistributable packageMsi packageExe
```

The packaging tasks write the app image and installers to
`build/compose/binaries/main/` (`app/`, `msi/` and `exe/`). The installers
include a Java runtime, so end users don't need to install Java.

The runtime is linked with ZIP resource compression (`--compress=2`). The UI
includes only its fifteen outlined Material icon definitions, copied from the
pinned Compose Material 1.7.3 sources in `src/main/kotlin/dev/puckmouse/icons/`.
Their Apache license and provenance notice ship with the app. When adding an
icon, include its source and attribution instead of bundling the extended icon
catalogue.

`run` starts the real app. Once resumed, it controls the desktop pointer. To run
it without desktop output, use QA mode (see below).

## Pinned dependencies

| Component | Version |
| --- | --- |
| Kotlin | 2.4.20 |
| Compose Multiplatform | 1.12.1 |
| JNA | 5.19.1 |
| Gradle (wrapper, checksum-verified) | 9.7.0 |
| Puck DLL | 2.0.0-alpha.2, ABI 1 |

The Puck DLL is stored in `native-resources/windows/` alongside its license,
notice and build information. Its SHA-256 checksum is pinned in
`PuckEngine.kt`. The app won't load a DLL that doesn't match the checksum, and it
checks the ABI version before using the DLL.

## Source layout

All sources are in `src/main/kotlin/dev/puckmouse/`.

| File | Responsibility |
| --- | --- |
| `Model.kt` | Settings model, defaults, presets and the UI controller. |
| `SettingsStore.kt` | Validation, migration of older settings files, and atomic save, import and export. |
| `InputLogic.kt` | Report decoding, the neutral gate, wheel accumulation and button tracking. |
| `PointerResponse.kt` | Radial pointer and independent scroll response shaping. |
| `LiveMotion.kt` | Arming and report-gap handling between device input and output. |
| `InputLoopTiming.kt` | Frame, device-refresh and feedback scheduling. |
| `PuckEngine.kt` | DLL loading, checksum and ABI checks, and engine commands. |
| `WindowsHost.kt` | Raw input, the global hotkey, `SendInput` output and the input thread. |
| `Ui.kt`, `UiTheme.kt` | Compose UI. |
| `Main.kt` | Entry point, tray, single-instance lock, QA and self-test modes. |

Native transport, motion processing, output injection, settings and UI stay
separate. All calls on a Puck handle, including destroying it, happen on the one
dedicated input thread. The UI never holds an engine handle.

The desktop adapter shapes normalized input into four independent output slots
before Puck smooths and integrates velocity. A separate native ownership channel
carries actual-neutral evidence, so a shaped zero inside a dead zone can't rearm
output after interruption. Assigning the same physical axis to pointer and scroll
doesn't couple their response curves.

## Behavior to preserve

- The app starts paused.
- Hard interruptions are pauses (including the clutch), disconnects, processing
  failures and mapping or button changes. Each one releases held buttons and
  discards pending motion. Output resumes only after an actual neutral report
  with no buttons held.
- A short gap in motion reports is not a hard interruption. Output stops while
  reports are missing. When fresh input arrives, motion continues automatically
  without a neutral report, held buttons stay held, and movement from the gap
  is discarded rather than replayed. A gap never counts as a neutral report.
- Device support is decided by the measured report profile, not by vendor ID
  alone. The only enabled profile is the `256f:c63a` combined Bluetooth report.
- A conflicting pause shortcut must be shown in the UI and must not disable the
  shortcut that's currently working.
- Tests and the preview sandbox never inject desktop input.
- There's no telemetry, network account or automatic startup registration.

## Testing

`./gradlew.bat test` runs unit and Compose UI tests. They cover:

- the Puck DLL integration
- synthetic device reports, including unknown and malformed ones
- pause, rearm, disconnect and mapping-change handling
- report gaps and frame timing
- settings validation and migration
- Windows registrations
- UI interactions
- pointer direction and speed across angles, report cadence, and settings migration

To save screenshots from the UI tests, pass a directory:

```powershell
./gradlew.bat test '-Dpuckmouse.screenshots=build/screenshots'
```

### Validation scope

The automated checks are synthetic. They run against the real Puck DLL with
generated reports and in-process UI tests, but they never use a physical device
and never inject desktop input.

Puck measured the `256f:c63a` report profile separately. That measurement
doesn't count as validation of this app. Formal physical validation of the
desktop app is incomplete. That includes:

- device-specific axis directions
- comfort and latency
- coexistence with the 3Dconnexion driver

Don't describe synthetic results as hardware testing, and don't claim
compatibility beyond the enabled report profile.

### QA mode

QA mode starts the app in the preview sandbox. Desktop output is disabled,
settings start from defaults and aren't saved, and the tray and single-instance
lock are skipped. For a packaged build, pass `--qa`:

```powershell
& 'build/compose/binaries/main/app/PuckMouse/PuckMouse.exe' --qa
```

With `gradlew run`, set the `PUCK_MOUSE_QA=1` environment variable. This sample
restores the variable's previous value afterwards:

```powershell
$previous = $env:PUCK_MOUSE_QA
try { $env:PUCK_MOUSE_QA = '1'; ./gradlew.bat run }
finally { $env:PUCK_MOUSE_QA = $previous }
```

The `puckmouse.qa=true` system property also enables QA mode.

### Packaged self-test

`PuckMouse.exe --self-test <result.txt>` loads the bundled runtime and DLL. It
runs a short motion and interruption check, writes the result file and exits
with a non-zero status on failure.

## Continuous integration

The GitHub Actions workflow in `.github/workflows/windows.yml` runs on Windows
for each push and pull request. It runs the tests, builds the app image and both
installers, runs the packaged self-test, and uploads the installers and test
reports as artifacts.

## Versioning

The version is set once in `build.gradle.kts`. It's used for the About section
and the installer version, and the installer drops the pre-release suffix.
Record user-facing changes in [CHANGELOG.md](../CHANGELOG.md).
