# Puck Mouse

Windows x64 desktop utility in Kotlin/JVM and Compose Desktop. Puck supplies
motion processing through its released C ABI. Keep native transport, processing,
output injection, persistent settings and UI separate. All operations on a Puck
handle, including destruction, stay on one dedicated OS thread.

Run the Gradle wrapper test and packaging tasks. Use Compose semantic UI tests
and the development-only Hot Reload MCP for UI verification. Inspect rendered
desktop and compact layouts. No global input injection from tests or preview.
Start paused, release held buttons on interruptions and require actual neutral
input before resuming. Hotkey conflicts must be visible and recoverable.

Do not infer device support from vendor ID alone. Current profile is the Puck
measured 256f:c63a combined Bluetooth report. Test unknown/malformed reports,
disconnects, mapping changes and pause/rearm. Hardware compatibility and timing
need physical validation; distinguish it from synthetic checks.

Public docs and releases describe the product, installation, hardware, tested
scope and limitations. Keep local paths, account details, agent notes and
operational status in ignored work/. No telemetry, network account or automatic
startup registration. Dependencies and native artifacts are pinned and verified.
