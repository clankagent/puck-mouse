package dev.puckmouse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import dev.puckmouse.icons.ArrowDropDown
import dev.puckmouse.icons.Bluetooth
import dev.puckmouse.icons.BluetoothDisabled
import dev.puckmouse.icons.Check
import dev.puckmouse.icons.ControlCamera
import dev.puckmouse.icons.ErrorOutline
import dev.puckmouse.icons.ExpandLess
import dev.puckmouse.icons.ExpandMore
import dev.puckmouse.icons.Info
import dev.puckmouse.icons.Layers
import dev.puckmouse.icons.Mouse
import dev.puckmouse.icons.Pause
import dev.puckmouse.icons.PlayArrow
import dev.puckmouse.icons.Science
import dev.puckmouse.icons.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path as FilePath
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt
import java.awt.event.KeyEvent as AwtKey

// ---------------------------------------------------------------------------------------------
// Shell
// ---------------------------------------------------------------------------------------------

private enum class Screen(val label: String, val tag: String, val icon: ImageVector) {
    MAPPINGS("Mappings", "nav-mappings", Icons.Outlined.ControlCamera),
    BUTTONS("Buttons", "nav-buttons", Icons.Outlined.Mouse),
    PROFILES("Profiles", "nav-profiles", Icons.Outlined.Layers),
    SETTINGS("Settings", "nav-settings", Icons.Outlined.Tune),
}

private sealed interface DialogRequest
private data class ConfirmRequest(val title: String, val body: String, val action: String, val onConfirm: () -> Unit) : DialogRequest
private data class NameRequest(val title: String, val initial: String, val action: String, val onConfirm: (String) -> Unit) : DialogRequest

@Composable
fun PuckMouseApp(controller: AppController) {
    val state by controller.state.collectAsState()
    var screen by remember { mutableStateOf(Screen.MAPPINGS) }
    var dialog by remember { mutableStateOf<DialogRequest?>(null) }
    val ask: (DialogRequest) -> Unit = { dialog = it }
    PuckTheme {
        Surface(Modifier.fillMaxSize(), color = PuckColors.Background, contentColor = PuckColors.Foreground) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val compact = maxWidth < 760.dp
                Column(Modifier.fillMaxSize()) {
                    Header(state, controller, compact)
                    HorizontalDivider(color = PuckColors.Line)
                    if (compact) {
                        CompactNav(screen) { screen = it }
                        HorizontalDivider(color = PuckColors.Line)
                        ScreenHost(screen, state, controller, ask, compact = true)
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            Rail(screen, state) { screen = it }
                            VerticalDivider(color = PuckColors.Line)
                            Box(Modifier.weight(1f).fillMaxHeight()) { ScreenHost(screen, state, controller, ask, compact = false) }
                        }
                    }
                }
            }
            dialog?.let { DialogHost(it) { dialog = null } }
        }
    }
}

@Composable
private fun ScreenHost(screen: Screen, state: AppState, controller: AppController, ask: (DialogRequest) -> Unit, compact: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Banners(state, controller, compact)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (screen) {
                Screen.MAPPINGS -> MappingsScreen(state, controller, ask, compact)
                Screen.BUTTONS -> ButtonsScreen(state, controller, compact)
                Screen.PROFILES -> ProfilesScreen(state, controller, ask, compact)
                Screen.SETTINGS -> SettingsScreen(state, controller, compact)
            }
        }
    }
}

@Composable
private fun Header(state: AppState, controller: AppController, compact: Boolean) {
    Surface(color = PuckColors.Surface) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val padding = Modifier.padding(horizontal = if (compact) Space.l else Space.xl, vertical = Space.m)
            if (maxWidth >= 1000.dp) {
                Row(padding.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Identity(state, controller, Modifier.weight(1f))
                    DeviceStatus(state)
                    OutputStatus(state)
                    PreviewToggle(state, controller)
                    PauseButton(state, controller)
                }
            } else {
                Column(padding.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        Identity(state, controller, Modifier.weight(1f))
                        PauseButton(state, controller)
                    }
                    Wrap {
                        DeviceStatus(state)
                        OutputStatus(state)
                        PreviewToggle(state, controller)
                    }
                }
            }
        }
    }
}

@Composable
private fun Identity(state: AppState, controller: AppController, modifier: Modifier) {
    Column(modifier) {
        Text("Puck Mouse", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        ProfileSwitcher(state, controller)
    }
}

@Composable
private fun ProfileSwitcher(state: AppState, controller: AppController) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.focusRing(4.dp).clip(MaterialTheme.shapes.extraSmall)
                .clickable(onClickLabel = "Switch profile", role = Role.DropdownList) { open = true }
                .testTag("profile-switcher").semantics(mergeDescendants = true) {}
                .padding(vertical = 2.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Profile  ", style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
            Text(state.profile.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp))
            Icon(Icons.Outlined.ArrowDropDown, null, tint = PuckColors.Secondary, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(open, { open = false }) {
            state.settings.profiles.forEach { p ->
                val selected = p.id == state.settings.selectedId
                DropdownMenuItem(
                    text = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 320.dp)) },
                    onClick = { open = false; if (!selected) controller.selectProfile(p.id) },
                    leadingIcon = { CheckSlot(selected) },
                )
            }
        }
    }
}

private enum class Tone { Active, Attention, Neutral }

private fun outputStatus(s: AppState): Pair<String, Tone> = when {
    s.preview -> "Preview only · desktop output off" to Tone.Neutral
    s.paused -> "Paused" to Tone.Attention
    !s.connected -> "Waiting for device" to Tone.Neutral
    !s.armed -> "Release the cap to resume" to Tone.Attention
    else -> "Active" to Tone.Active
}

@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.heightIn(min = 32.dp).border(1.dp, PuckColors.Line, MaterialTheme.shapes.small).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s), content = content,
    )
}

@Composable
private fun DeviceStatus(state: AppState) {
    Pill(Modifier.testTag("device-status").semantics(mergeDescendants = true) {}) {
        Icon(if (state.connected) Icons.Outlined.Bluetooth else Icons.Outlined.BluetoothDisabled, null,
            tint = if (state.connected) PuckColors.Active else PuckColors.Secondary, modifier = Modifier.size(16.dp))
        Text(if (state.connected) state.deviceName else state.deviceName.ifBlank { "No SpaceMouse connected" },
            style = MaterialTheme.typography.labelMedium, color = if (state.connected) PuckColors.Foreground else PuckColors.Secondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
    }
}

@Composable
private fun OutputStatus(state: AppState) {
    val (text, tone) = outputStatus(state)
    Pill(Modifier.testTag("output-status").semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        StatusDot(tone)
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = when (tone) { Tone.Active -> PuckColors.Active; Tone.Attention -> PuckColors.Paused; Tone.Neutral -> PuckColors.Foreground })
    }
}

@Composable
private fun StatusDot(tone: Tone) {
    val m = Modifier.size(8.dp).clip(CircleShape)
    when (tone) {
        Tone.Active -> Box(m.background(PuckColors.Active))
        Tone.Attention -> Box(m.background(PuckColors.Paused))
        Tone.Neutral -> Box(m.border(1.5.dp, PuckColors.Secondary, CircleShape))
    }
}

@Composable
private fun PreviewToggle(state: AppState, controller: AppController) {
    Row(
        Modifier.heightIn(min = 32.dp).focusRing().clip(MaterialTheme.shapes.small)
            .border(1.dp, if (state.preview) PuckColors.Warm else PuckColors.Control, MaterialTheme.shapes.small)
            .toggleable(state.preview, role = Role.Switch) { controller.setPreview(it) }
            .testTag("preview-toggle")
            .semantics { contentDescription = "Preview sandbox"; stateDescription = if (state.preview) "On" else "Off" }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Icon(Icons.Outlined.Science, null, modifier = Modifier.size(16.dp), tint = if (state.preview) PuckColors.Warm else PuckColors.Secondary)
        Text("Preview", style = MaterialTheme.typography.labelMedium)
        Text(if (state.preview) "On" else "Off", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = if (state.preview) PuckColors.Warm else PuckColors.Secondary)
    }
}

@Composable
private fun PauseButton(state: AppState, controller: AppController) {
    Button(
        onClick = { controller.togglePause() },
        modifier = Modifier.heightIn(min = 40.dp).testTag("pause-toggle"),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = PuckColors.Warm, contentColor = PuckColors.Background),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s))
        Text(if (state.paused) "Resume" else "Pause", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(state.settings.hotkey.label, style = MaterialTheme.typography.labelMedium, color = PuckColors.Background.copy(alpha = .62f), maxLines = 1)
    }
}

@Composable
private fun Rail(screen: Screen, state: AppState, onSelect: (Screen) -> Unit) {
    Column(Modifier.width(180.dp).fillMaxHeight().padding(Space.m)) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Screen.entries.forEach { NavItem(it, it == screen, Modifier.fillMaxWidth()) { onSelect(it) } }
        }
        Spacer(Modifier.weight(1f))
        SaveIndicator(state)
    }
}

@Composable
private fun CompactNav(screen: Screen, onSelect: (Screen) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = 6.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        Screen.entries.forEach { NavItem(it, it == screen, Modifier.weight(1f), stacked = true) { onSelect(it) } }
    }
}

@Composable
private fun NavItem(item: Screen, selected: Boolean, modifier: Modifier = Modifier, stacked: Boolean = false, onClick: () -> Unit) {
    val tint = if (selected) PuckColors.Foreground else PuckColors.Secondary
    val base = modifier.height(if (stacked) 52.dp else 40.dp).focusRing().clip(MaterialTheme.shapes.small)
        .background(if (selected) PuckColors.Selected else Color.Transparent)
        .selectable(selected, role = Role.Tab, onClick = onClick)
        .testTag(item.tag)
    val label: @Composable () -> Unit = {
        Text(item.label, style = if (stacked) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (stacked) {
        Column(base.padding(horizontal = Space.xs), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(item.icon, null, tint = tint, modifier = Modifier.size(20.dp))
            label()
        }
    } else {
        Row(base.padding(horizontal = Space.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Icon(item.icon, null, tint = tint, modifier = Modifier.size(20.dp))
            label()
        }
    }
}

@Composable
private fun SaveIndicator(state: AppState) {
    val saved = state.message == "Saved"
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Check, null, tint = if (saved) PuckColors.Active else PuckColors.Secondary, modifier = Modifier.size(14.dp))
        Text(if (saved) "Saved" else "Changes save automatically", style = MaterialTheme.typography.labelSmall, color = PuckColors.Secondary)
    }
}

@Composable
private fun Banners(state: AppState, controller: AppController, compact: Boolean) {
    val info = state.message?.takeIf { it != "Saved" }
    if (state.error == null && info == null) return
    Column(Modifier.fillMaxWidth().padding(start = pagePad(compact), end = pagePad(compact), top = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        state.error?.let { Banner(it, error = true, tag = "error-dismiss") { controller.dismissMessage() } }
        info?.let { Banner(it, error = false, tag = "message-dismiss") { controller.dismissMessage() } }
    }
}

@Composable
private fun Banner(text: String, error: Boolean, tag: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .background(if (error) PuckColors.ErrorContainer else PuckColors.Raised)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }
            .padding(start = Space.m, end = Space.xs, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, null, tint = if (error) PuckColors.Error else PuckColors.Secondary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = Space.s))
        TextButton(onClick = onDismiss, modifier = Modifier.testTag(tag)) { Text("Dismiss") }
    }
}

// ---------------------------------------------------------------------------------------------
// Mappings
// ---------------------------------------------------------------------------------------------

@Composable
private fun MappingsScreen(state: AppState, controller: AppController, ask: (DialogRequest) -> Unit, compact: Boolean) {
    var selected by remember { mutableStateOf(Output.POINTER_X) }
    var advanced by remember { mutableStateOf(false) }
    val profile = state.profile
    val mappings = Output.entries.mapNotNull { o -> profile.mappings.firstOrNull { it.output == o } }
    val mapping = mappings.firstOrNull { it.output == selected } ?: mappings.first()
    val pad = pagePad(compact)

    val intro: @Composable () -> Unit = {
        PageHeader("Mappings", "Choose which cap movement drives each pointer and scroll output.") {
            TextButton(
                onClick = {
                    ask(ConfirmRequest("Reset “${profile.name}” to defaults?",
                        "This replaces all four movement mappings and both button actions with the defaults. The profile name stays the same.",
                        "Reset") { controller.resetProfile() })
                },
                modifier = Modifier.testTag("mapping-reset"),
            ) { Text("Reset to defaults…") }
        }
        StatusNotice(state, controller)
    }
    val list: @Composable (Modifier) -> Unit = { m -> MappingList(mappings, mapping.output, profile::tuningLinked, controller, m) { selected = it } }
    val editor: @Composable (Modifier) -> Unit = { m ->
        MappingEditor(mapping, mappings, profile, state.axes, state.connected || state.preview,
            advanced, { advanced = it }, controller, m, compact)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        when {
            maxWidth >= 1100.dp -> Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(pad), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                    intro()
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                        list(Modifier.width(300.dp))
                        editor(Modifier.weight(1f))
                    }
                }
                VerticalDivider(color = PuckColors.Line)
                LiveInspector(state, controller, collapsible = false, startExpanded = true,
                    modifier = Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(Space.l))
            }
            maxWidth >= 640.dp -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                intro()
                Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                    Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                        list(Modifier.fillMaxWidth())
                        LiveInspector(state, controller, collapsible = true, startExpanded = true,
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(PuckColors.Surface).padding(Space.l))
                    }
                    editor(Modifier.weight(1f))
                }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                intro()
                list(Modifier.fillMaxWidth())
                editor(Modifier.fillMaxWidth())
                LiveInspector(state, controller, collapsible = true, startExpanded = state.preview,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(PuckColors.Surface).padding(Space.l))
            }
        }
    }
}

@Composable
private fun StatusNotice(state: AppState, controller: AppController) {
    val shortcut = state.settings.hotkey.label
    when {
        state.preview -> Notice(Icons.Outlined.Science, PuckColors.Warm, "Preview sandbox · desktop output off",
            "Drag the movement sliders under Live input to see how each mapping responds and which way the pointer heads. Nothing is sent to Windows.")
        !state.connected -> Notice(Icons.Outlined.BluetoothDisabled, PuckColors.Secondary, state.deviceName.ifBlank { "No SpaceMouse connected" },
            "Puck Mouse currently supports the 3Dconnexion SpaceMouse that identifies over Bluetooth as 256f:c63a. " +
                "Pair it in Windows Bluetooth settings and it is picked up automatically. Other models aren't supported yet.") {
            OutlinedButton(onClick = { controller.setPreview(true) }, modifier = Modifier.testTag("preview-try"), shape = MaterialTheme.shapes.small) {
                Text("Try without device")
            }
        }
        state.paused -> Notice(Icons.Outlined.Pause, PuckColors.Paused, "Paused — the SpaceMouse isn't moving the pointer",
            "Puck Mouse always starts paused. Choose Resume or press $shortcut when you're ready. Movement starts once the cap rests at center.")
        !state.armed -> Notice(Icons.Outlined.Info, PuckColors.Paused, "Release the cap to resume",
            "Let go of the cap. Output resumes as soon as it rests at center.")
    }
}

@Composable
private fun Notice(icon: ImageVector, accent: Color, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(PuckColors.Surface).padding(Space.l)
            .semantics(mergeDescendants = action == null) {},
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (accent == PuckColors.Secondary) PuckColors.Foreground else accent)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary)
            if (action != null) { Spacer(Modifier.height(Space.xs)); action() }
        }
    }
}

@Composable
private fun MappingList(
    mappings: List<Mapping>, selected: Output, linked: (Output) -> Boolean, controller: AppController, modifier: Modifier, onSelect: (Output) -> Unit,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("Outputs")
        mappings.forEach { m ->
            val shared = m.enabled && mappings.any { it.output != m.output && it.enabled && it.axis == m.axis }
            val isSelected = m.output == selected
            Row(
                Modifier.fillMaxWidth().focusRing(8.dp).clip(MaterialTheme.shapes.medium)
                    .background(if (isSelected) PuckColors.Selected else PuckColors.Surface)
                    .border(1.dp, if (isSelected) PuckColors.Control else Color.Transparent, MaterialTheme.shapes.medium)
                    .selectable(isSelected, role = Role.Tab) { onSelect(m.output) }
                    .testTag("mapping-${m.output.name}")
                    .padding(start = 14.dp, end = Space.s, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(m.output.label, style = MaterialTheme.typography.titleSmall, color = if (m.enabled) PuckColors.Foreground else PuckColors.Secondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (!m.enabled) "Off" else listOfNotNull(m.axis.label, topSpeedText(m), if (m.inverted) "reversed" else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val isLinked = linked(m.output)
                    if (shared || isLinked) Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        if (isLinked) Text("Tuning linked", style = MaterialTheme.typography.labelSmall, color = PuckColors.Secondary,
                            modifier = Modifier.testTag("mapping-linked-${m.output.name}"))
                        if (shared) Text("Shared movement", style = MaterialTheme.typography.labelSmall, color = PuckColors.Paused)
                    }
                }
                Switch(m.enabled, { controller.updateMapping(m.copy(enabled = it)) }, colors = puckSwitchColors(),
                    modifier = Modifier.testTag("mapping-enabled-${m.output.name}").semantics { contentDescription = "${m.output.label} enabled" })
            }
        }
    }
}

@Composable
private fun MappingEditor(
    m: Mapping, mappings: List<Mapping>, profile: Profile, axes: Axes, hasLive: Boolean,
    advanced: Boolean, onAdvanced: (Boolean) -> Unit, controller: AppController, modifier: Modifier, compact: Boolean,
) {
    val update: (Mapping) -> Unit = { next -> if (next != m) controller.updateMapping(next) }
    val linked = profile.tuningLinked(m.output)
    val partner = m.output.partner().label
    val pointerX = profile.pointer(Output.POINTER_X)
    val pointerY = profile.pointer(Output.POINTER_Y)
    val combined = m.output.isPointer && profile.keepsDirection()
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(PuckColors.Surface).padding(if (compact) Space.l else Space.xl).testTag("mapping-editor"),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(m.output.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(m.sentence(), style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary)
        }
        ToggleRow("Enabled", "Turn off to stop this output but keep its settings.", m.enabled, "mapping-enabled") { update(m.copy(enabled = it)) }
        ToggleRow(
            if (m.output.isPointer) "Link pointer X/Y tuning" else "Link scroll X/Y tuning",
            (if (linked) "Speed, dead zone, curve, full-speed point and smoothing are shared with $partner."
            else "Turning on copies this tuning to $partner and keeps speed, dead zone, curve, full-speed point and smoothing shared.") +
                " Movement, reverse direction and on/off stay separate.",
            linked, "link-tuning",
        ) { controller.setTuningLinked(m.output, it) }
        if (m.output.isPointer) {
            ToggleRow(
                "Preserve pointer direction",
                (if (profile.radialPointer) "How far you push in any direction sets the speed, and the pointer travels at the same angle as your hand. " +
                    "Angles stay true when horizontal and vertical tuning match — linked tuning keeps them matched." +
                    (if (!profile.pointerTuningMatched()) " Their tuning differs right now, which can change the angle of slanted moves noticeably." else "")
                else "Horizontal and vertical are shaped separately, so slanted moves can bend toward straight lines.") +
                    " Applies to both pointer directions.",
                profile.radialPointer, "pointer-radial",
            ) { controller.setRadialPointer(it) }
            if (profile.radialPointer && pointerX.axis == pointerY.axis) {
                InlineNote("Both pointer directions use ${pointerX.axis.label}. One movement can't point in two dimensions, " +
                    "so each direction is shaped separately.")
            }
        }
        HorizontalDivider(color = PuckColors.Line)

        Field("Movement") {
            Picker(
                title = m.axis.label, subtitle = m.axis.hint, tag = "source-selector", description = "Movement for ${m.output.label}",
                options = Axis.entries, isSelected = { it == m.axis }, optionTag = { "source-${it.name}" },
                optionTitle = { it.label },
                optionSubtitle = { a ->
                    val users = mappings.filter { it.output != m.output && it.enabled && it.axis == a }
                    if (users.isEmpty()) a.hint else "${a.hint} · also drives ${users.joinToString { it.output.label }}"
                },
            ) { update(m.copy(axis = it)) }
        }
        val others = mappings.filter { it.output != m.output && it.enabled && it.axis == m.axis }
        if (m.enabled && others.isNotEmpty()) {
            InlineNote("${m.axis.label} also drives ${others.joinToString { it.output.label }}. Both outputs respond at the same time. " +
                "That's allowed — pick a different movement if you want them separate.")
        }

        val range = speedRange(m.output)
        LabeledSlider(
            label = "Top speed", value = speedText(m.output, m.speed), tag = "speed-slider",
            caption = when {
                !m.output.isPointer -> "Fastest scrolling"
                combined && profile.pointerTuningMatched() -> "Fastest total pointer speed in any direction, diagonals included"
                combined -> "Fastest pointer speed when moving straight ${if (m.output == Output.POINTER_X) "left or right" else "up or down"}; " +
                    "slanted moves blend both directions' tuning"
                else -> "Fastest ${if (m.output == Output.POINTER_X) "horizontal" else "vertical"} pointer speed; " +
                    "slanted moves add both directions and can go faster"
            } + ", reached at the full-speed point" + (if (m.fullSpeedAt < 1) " (${percent(m.fullSpeedAt)} push)." else " (full push)."),
            current = m.speed.toFloat().coerceIn(range), range = range,
        ) { update(m.copy(speed = roundSpeed(m.output, it.toDouble()))) }

        if (m.output.isPointer) {
            val natural = naturalPointerTuning(m)
            val naturalApplied = profile.radialPointer && profile.linkPointerTuning &&
                listOf(pointerX, pointerY).all { naturalPointerTuning(it) == it }
            val preset = pointerTravelTuning(m)
            val applied = m.withTuningFrom(preset) == m
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Wrap {
                    Button(onClick = { controller.applyNaturalPointerPreset() }, enabled = !naturalApplied, shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = PuckColors.Warm, contentColor = PuckColors.Background,
                            disabledContainerColor = PuckColors.Selected, disabledContentColor = PuckColors.Foreground),
                        modifier = Modifier.testTag("natural-pointer-preset")) {
                        if (naturalApplied) { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(Space.s)) }
                        Text("Natural pointer")
                    }
                    OutlinedButton(onClick = { controller.applyPointerTravelPreset(m.output) }, enabled = !applied, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.testTag("pointer-travel-preset")) {
                        if (applied) { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(Space.s)) }
                        Text("Precision + fast travel")
                    }
                }
                Text("Natural pointer (recommended): gentle start with a ${percent(natural.deadzone)} dead zone and curve ${trimNumber(natural.curve)}, " +
                    (if (natural.responseMs == 0.0) "no smoothing" else "${natural.responseMs.roundToInt()} ms smoothing") +
                    ", up to ${speedText(m.output, natural.speed)}" + (if (natural.fullSpeedAt < 1) " from a ${percent(natural.fullSpeedAt)} push." else " at full push.") +
                    " Sets both pointer directions, links their tuning and preserves pointer direction. Keeps movements, reversed directions and on/off.",
                    style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
                Text("Precision + fast travel: slow near center, ${speedText(m.output, preset.speed)} from a ${percent(preset.fullSpeedAt)} push" +
                    (if (linked) " — applies to both pointer directions." else ".") + " Useful to compare; keeps movements, reversed directions, on/off and Preserve pointer direction.",
                    style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
            }
        } else {
            val applied = gentleScrollTuning(m) == m
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                OutlinedButton(onClick = { controller.applyGentleScrollPreset(m.output) }, enabled = !applied, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag("gentle-scroll-preset")) {
                    if (applied) { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(Space.s)) }
                    Text("Gentle scrolling")
                }
                Text("14% dead zone, curve 1.7 and full speed at full push" +
                    (if (linked) " — applies to both scroll directions." else ".") + " Keeps speed, smoothing and movement assignments.",
                    style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
            }
        }

        ToggleRow("Reverse direction", "Use this if ${m.output.effectNoun()} moves the opposite way to your hand.", m.inverted, "invert-toggle") {
            update(m.copy(inverted = it))
        }
        HorizontalDivider(color = PuckColors.Line)

        Row(
            Modifier.fillMaxWidth().focusRing().clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = if (advanced) "Hide fine tuning" else "Show fine tuning", role = Role.Button) { onAdvanced(!advanced) }
                .testTag("advanced-toggle")
                .semantics { stateDescription = if (advanced) "Expanded" else "Collapsed" }
                .padding(vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Fine tuning", style = MaterialTheme.typography.titleSmall)
                Text("Dead zone ${percent(m.deadzone)} · Curve ${"%.2f".format(m.curve)} · Full speed at ${percent(m.fullSpeedAt)} · " +
                    "Smoothing ${m.responseMs.roundToInt()} ms",
                    style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
            }
            Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = PuckColors.Secondary)
        }
        if (advanced) {
            if (combined) CurvePreview(m, pointerMagnitude(profile, axes), hasLive, targetVector(profile, axes).length, profile.pointerTuningMatched())
            else CurvePreview(m, axes[m.axis], hasLive)
            LabeledSlider("Dead zone", percent(m.deadzone), "deadzone-slider",
                "Small movements inside this range are ignored, so a resting hand doesn't drift.",
                m.deadzone.toFloat().coerceIn(0f, .5f), 0f..0.5f) {
                val deadzone = (it * 100).roundToInt() / 100.0
                update(m.copy(deadzone = deadzone, fullSpeedAt = maxOf(m.fullSpeedAt, fullSpeedFloor(deadzone))))
            }
            val floor = fullSpeedFloor(m.deadzone)
            LabeledSlider("Full speed at", "${percent(m.fullSpeedAt)} push", "full-speed-slider",
                "How far you push to reach top speed. Lower reaches it sooner; pushing further adds nothing.",
                m.fullSpeedAt.toFloat().coerceIn(floor.toFloat(), 1f), floor.toFloat()..1f) {
                update(m.copy(fullSpeedAt = ((it * 100).roundToInt() / 100.0).coerceIn(floor, 1.0)))
            }
            LabeledSlider("Response curve", "%.2f · %s".format(m.curve, curveName(m.curve)), "curve-slider",
                "Above 1 keeps small pushes slow for precision. Raising it alone also slows medium pushes — " +
                    "use Full speed at and Top speed to keep travel fast. Below 1 reacts quickly to small pushes.",
                m.curve.toFloat().coerceIn(.5f, 12f), .5f..12f) { update(m.copy(curve = (it * 20).roundToInt() / 20.0)) }
            LabeledSlider("Smoothing", "${m.responseMs.roundToInt()} ms", "smoothing-slider",
                "Evens out shaky motion. Higher values feel steadier but react a little later.",
                m.responseMs.toFloat().coerceIn(0f, 150f), 0f..150f) { update(m.copy(responseMs = (it / 5).roundToInt() * 5.0)) }
            val defaults = defaultMappings().first { it.output == m.output }
            val restored = m.copy(deadzone = defaults.deadzone, curve = defaults.curve, responseMs = defaults.responseMs, fullSpeedAt = defaults.fullSpeedAt)
            if (restored != m) {
                TextButton(onClick = { update(restored) }, modifier = Modifier.testTag("advanced-defaults")) { Text("Restore default fine tuning") }
            }
        }
    }
}

/** Lowest valid full-speed point for a dead zone, rounded up to whole percent so it always passes validation. */
private fun fullSpeedFloor(deadzone: Double): Double = ceil(maxOf(.1, deadzone + .05) * 100 - 1e-6) / 100

private fun speedAt(x: Double, m: Mapping): String {
    val v = shapedPush(x, m) * m.speed
    return if (m.output.isPointer) "${v.roundToInt()} ${m.output.unit}" else "%.1f ${m.output.unit}".format(v)
}

/**
 * [combinedSpeed] is set when the pointer keeps its direction: [live] is then the combined push of both pointer
 * movements and the speed is the shaped pointer target from both directions, not this direction's share.
 */
@Composable
private fun CurvePreview(m: Mapping, live: Double, hasLive: Boolean, combinedSpeed: Double? = null, matched: Boolean = true) {
    val deflection = abs(live).coerceIn(0.0, 1.0)
    val samples = listOf(.1, .3, .6)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Response shape", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (hasLive) Text(
                if (combinedSpeed != null) "Combined push ${percent(deflection)} → ${speedText(m.output, combinedSpeed)}"
                else "Now: ${percent(deflection)} → ${speedAt(deflection, m)}",
                style = MaterialTheme.typography.labelMedium.tabular(), color = PuckColors.Active,
                modifier = Modifier.testTag("curve-live"))
        }
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.small).background(PuckColors.Background)
                .semantics {
                    contentDescription = (if (combinedSpeed != null) "Response to combined push: " else "Response shape: ") +
                        "dead zone ${percent(m.deadzone)}, curve ${"%.2f".format(m.curve)}, " +
                        "full speed ${speedText(m.output, m.speed)} from ${percent(m.fullSpeedAt)} push. " +
                        samples.joinToString { "${percent(it)} push gives ${speedAt(it, m)}" } + "."
                },
        ) {
            val w = size.width; val h = size.height
            for (i in 1..3) {
                drawLine(PuckColors.Line, Offset(w * i / 4, 0f), Offset(w * i / 4, h), 1f)
                drawLine(PuckColors.Line, Offset(0f, h * i / 4), Offset(w, h * i / 4), 1f)
            }
            drawRect(PuckColors.Track.copy(alpha = .7f), Offset.Zero, Size((w * m.deadzone).toFloat(), h))
            val fx = (w * m.fullSpeedAt).toFloat()
            if (m.fullSpeedAt < 1) drawRect(PuckColors.Warm.copy(alpha = .08f), Offset(fx, 0f), Size(w - fx, h))
            drawLine(PuckColors.Control.copy(alpha = .6f), Offset((w * m.deadzone).toFloat(), h), Offset(fx, 0f), 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            samples.forEach { x ->
                val px = (x * w).toFloat()
                drawLine(PuckColors.Control.copy(alpha = .5f), Offset(px, h - 6.dp.toPx()), Offset(px, h), 1.dp.toPx())
            }
            val path = Path()
            for (i in 0..200) {
                val x = i / 200.0
                val px = (x * w).toFloat(); val py = (h - shapedPush(x, m) * h).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, PuckColors.Warm, style = Stroke(2.dp.toPx()))
            if (hasLive) {
                val px = (deflection * w).toFloat(); val py = (h - shapedPush(deflection, m) * h).toFloat()
                drawLine(PuckColors.Active.copy(alpha = .5f), Offset(px, 0f), Offset(px, h), 1f)
                drawCircle(PuckColors.Active, 5.dp.toPx(), Offset(px, py))
            }
        }
        Row {
            Text("Center", style = MaterialTheme.typography.labelSmall, color = PuckColors.Secondary)
            Spacer(Modifier.weight(1f))
            Text(if (combinedSpeed != null) "Combined push →" else "Cap pushed →", style = MaterialTheme.typography.labelSmall, color = PuckColors.Secondary)
        }
        Wrap {
            samples.forEach { x -> SpeedChip("${percent(x)} push", speedAt(x, m)) }
            SpeedChip("${percent(m.fullSpeedAt)}+ push", speedText(m.output, m.speed), highlight = true)
        }
        if (combinedSpeed != null) {
            Text("Combined push is how far the cap is moved in any direction. It sets the pointer speed; your hand sets the direction. " +
                if (matched) "Both pointer directions share this shape, so these speeds hold whichever way you push."
                else "Horizontal and vertical tuning differ, so these speeds hold only for pushes straight along this direction.",
                style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
        }
        Text("Shaded left: dead zone. Tinted right: full speed. Dashed: the same range with curve 1.",
            style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
    }
}

@Composable
private fun SpeedChip(label: String, value: String, highlight: Boolean = false) {
    Row(
        Modifier.border(1.dp, if (highlight) PuckColors.Warm else PuckColors.Line, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Space.s, vertical = 2.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = PuckColors.Secondary)
        Text(value, style = MaterialTheme.typography.labelSmall.tabular(), color = PuckColors.Foreground)
    }
}

// ---------------------------------------------------------------------------------------------
// Live inspector
// ---------------------------------------------------------------------------------------------

@Composable
private fun LiveInspector(state: AppState, controller: AppController, collapsible: Boolean, startExpanded: Boolean, modifier: Modifier) {
    var expanded by remember { mutableStateOf(startExpanded) }
    LaunchedEffect(state.preview) { if (state.preview) expanded = true }
    val open = !collapsible || expanded
    val hasLive = state.connected || state.preview
    val mappings = state.profile.mappings
    Column(modifier.testTag("live-inspector"), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Row(
            (if (collapsible) Modifier.fillMaxWidth().focusRing().clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = if (expanded) "Hide live input" else "Show live input", role = Role.Button) { expanded = !expanded }
                .testTag("inspector-toggle").semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            else Modifier.fillMaxWidth()).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Live input", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            if (collapsible) Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = PuckColors.Secondary)
        }
        if (!open) return@Column
        if (state.preview) {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).border(1.dp, PuckColors.Warm, MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 8.dp).testTag("preview-label").semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Icon(Icons.Outlined.Science, null, tint = PuckColors.Warm, modifier = Modifier.size(16.dp))
                Text("Preview only · desktop output off", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = PuckColors.Warm)
            }
        }
        if (!hasLive) {
            Text("Movement appears here when a supported SpaceMouse is connected.", style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary)
            OutlinedButton(onClick = { controller.setPreview(true) }, shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("preview-try-inspector")) {
                Text("Try without device")
            }
            return@Column
        }
        SectionLabel(if (state.preview) "Movement · drag to simulate" else "Movement")
        Axis.entries.forEach { axis ->
            val users = mappings.filter { it.enabled && it.axis == axis }
            AxisRow(axis, state.axes[axis], users, state.preview, controller)
        }
        if (state.preview) {
            Wrap {
                OutlinedButton(onClick = { controller.centerPreview() }, shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("preview-center")) {
                    Text("Center all")
                }
                TextButton(onClick = { controller.setPreview(false) }, modifier = Modifier.testTag("preview-exit")) { Text("Exit preview") }
            }
        }
        HorizontalDivider(color = PuckColors.Line)
        DirectionPreview(state)
        HorizontalDivider(color = PuckColors.Line)
        SectionLabel("Output")
        Output.entries.forEach { o ->
            val m = mappings.firstOrNull { it.output == o }
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Text(o.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (m == null || !m.enabled) "Off" else rateText(o, state.outputs[o] ?: 0.0),
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = if ((state.outputs[o] ?: 0.0) != 0.0) PuckColors.Foreground else PuckColors.Secondary)
            }
        }
        val footnote = when {
            state.preview -> "Rates show what would be sent. Windows receives nothing."
            state.paused -> "Paused — output stays at zero until you resume."
            !state.armed -> "Waiting for the cap to rest at center."
            else -> null
        }
        footnote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary) }
    }
}

/** Screen-space vector: +x right, +y down, matching pointer output. */
private data class Vec(val x: Double, val y: Double) {
    val length get() = hypot(x, y)
    /** 0° right, 90° up. */
    val degrees get() = (Math.toDegrees(atan2(-y, x)) + 360) % 360
}

private const val SQRT2 = 1.4142135623730951

private fun Profile.pointer(o: Output) = mappings.first { it.output == o }

/** Mirrors shapedOutputs: direction is only kept when the two pointer outputs read different movements. */
private fun Profile.keepsDirection() = radialPointer && pointer(Output.POINTER_X).axis != pointer(Output.POINTER_Y).axis

private fun Profile.pointerTuningMatched() = pointer(Output.POINTER_X).let { it.withTuningFrom(pointer(Output.POINTER_Y)) == it }

/** Cap push as the pointer reads it: enabled pointer movements with reversal applied. */
private fun capVector(p: Profile, axes: Axes): Vec {
    fun read(o: Output) = p.pointer(o).let { if (!it.enabled) 0.0 else axes[it.axis] * if (it.inverted) -1.0 else 1.0 }
    return Vec(read(Output.POINTER_X), read(Output.POINTER_Y))
}

/** Shaped pointer rate before smoothing, from the same shaping the engine is fed. */
private fun targetVector(p: Profile, axes: Axes): Vec {
    val shaped = shapedOutputs(p, axes)
    return Vec(shaped[Output.POINTER_X.ordinal] * p.pointer(Output.POINTER_X).speed, shaped[Output.POINTER_Y.ordinal] * p.pointer(Output.POINTER_Y).speed)
}

private fun angleBetween(a: Vec, b: Vec): Double {
    val d = abs(a.degrees - b.degrees) % 360
    return if (d > 180) 360 - d else d
}

private fun degreesText(v: Vec) = "${v.degrees.roundToInt() % 360}°"

private fun DrawScope.arrow(from: Offset, to: Offset, color: Color, width: Float) {
    val d = to - from
    val length = d.getDistance()
    if (length < 1f) return
    val u = d / length; val n = Offset(-u.y, u.x)
    val head = minOf(length * .45f, width * 4f)
    drawLine(color, from, to, width, StrokeCap.Round)
    drawLine(color, to, to - u * head + n * (head * .6f), width, StrokeCap.Round)
    drawLine(color, to, to - u * head - n * (head * .6f), width, StrokeCap.Round)
}

@Composable
private fun DirectionPreview(state: AppState) {
    val p = state.profile
    val x = p.pointer(Output.POINTER_X); val y = p.pointer(Output.POINTER_Y)
    Column(Modifier.fillMaxWidth().testTag("direction-preview"), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Wrap {
            SectionLabel("Pointer direction")
            Text(when {
                p.keepsDirection() -> if (p.pointerTuningMatched()) "· Radial response" else "· Radial · tuning differs"
                p.radialPointer -> "· Separate axes (same movement)"
                else -> "· Separate axes"
            },
                style = MaterialTheme.typography.labelMedium, color = PuckColors.Foreground, modifier = Modifier.testTag("direction-mode"))
        }
        if (!x.enabled && !y.enabled) {
            Text("Both pointer outputs are off.", style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
            return@Column
        }
        val cap = capVector(p, state.axes)
        val target = targetVector(p, state.axes)
        val top = listOf(x, y).filter { it.enabled }.maxOf { it.speed }.coerceAtLeast(1.0)
        val hasCap = cap.length > 1e-6
        val moving = target.length > 1e-6
        val capText = if (hasCap) "${degreesText(cap)} · ${percent(minOf(cap.length, 1.0))} push" else "Centered"
        val targetText = if (moving) "${degreesText(target)} · ${target.length.roundToInt()} px/s" else "Still"
        val bend = if (hasCap && moving) angleBetween(cap, target) else 0.0
        val (status, tone) = when {
            !hasCap -> "Cap at center — push it to see a direction." to PuckColors.Secondary
            !moving -> "Inside the dead zone — the pointer stays still." to PuckColors.Secondary
            bend < .5 -> "Pointer follows the cap direction." to PuckColors.Active
            else -> "Pointer bends ${maxOf(1, bend.roundToInt())}° away from the cap." to PuckColors.Paused
        }
        val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier.size(112.dp).clip(MaterialTheme.shapes.small).background(PuckColors.Background)
                    .semantics { contentDescription = "Direction preview. Cap direction $capText. Target pointer $targetText, before smoothing." },
            ) {
                val c = center
                val half = size.minDimension / 2
                // Ring marks full push and top speed; leave room for diagonals up to √2 of it.
                val r = half * .68f
                val hair = 1.dp.toPx()
                drawLine(PuckColors.Line, Offset(0f, c.y), Offset(size.width, c.y), hair)
                drawLine(PuckColors.Line, Offset(c.x, 0f), Offset(c.x, size.height), hair)
                val d = half * .96f / SQRT2.toFloat()
                val dotted = PathEffect.dashPathEffect(floatArrayOf(2f, 5f))
                drawLine(PuckColors.Line, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), hair, pathEffect = dotted)
                drawLine(PuckColors.Line, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), hair, pathEffect = dotted)
                drawCircle(PuckColors.Track, r, c, style = Stroke(hair))
                fun at(v: Vec, scale: Double): Offset {
                    val k = minOf(v.length * scale, SQRT2) / v.length * r
                    return Offset(c.x + (v.x * k).toFloat(), c.y + (v.y * k).toFloat())
                }
                if (hasCap) {
                    val u = Offset((cap.x / cap.length).toFloat(), (cap.y / cap.length).toFloat())
                    drawLine(PuckColors.Control, c, c + u * (half * .96f), 1.5.dp.toPx(), pathEffect = dashed)
                    drawCircle(PuckColors.Control, 4.dp.toPx(), at(cap, 1.0), style = Stroke(1.5.dp.toPx()))
                }
                if (moving) arrow(c, at(target, 1.0 / top), PuckColors.Warm, 2.dp.toPx())
                drawCircle(PuckColors.Control, 2.dp.toPx(), c)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                LegendItem("Cap direction", capText, "direction-cap") {
                    drawLine(PuckColors.Control, Offset(0f, center.y), Offset(size.width, center.y), 1.5.dp.toPx(), pathEffect = dashed)
                }
                LegendItem("Target pointer", targetText, "direction-pointer") {
                    arrow(Offset(0f, center.y), Offset(size.width, center.y), PuckColors.Warm, 2.dp.toPx())
                }
            }
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = tone, modifier = Modifier.testTag("direction-status"))
        listOf(x, y).firstOrNull { !it.enabled }?.let {
            Text("${it.output.label} is off, so the pointer moves along one line only.", style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
        }
        Text("0° is right, 90° is up. Target pointer is the direction and speed the response asks for, before smoothing." +
            if (state.preview) " Preview only — nothing is sent to Windows." else "",
            style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
    }
}

@Composable
private fun LegendItem(title: String, value: String, tag: String, swatch: DrawScope.() -> Unit) {
    Row(Modifier.testTag(tag).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Canvas(Modifier.padding(top = 3.dp).size(width = 16.dp, height = 12.dp), onDraw = swatch)
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.labelSmall.tabular(), color = PuckColors.Secondary)
        }
    }
}

@Composable
private fun AxisRow(axis: Axis, value: Double, users: List<Mapping>, preview: Boolean, controller: AppController) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(axis.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (users.isEmpty()) PuckColors.Secondary else PuckColors.Foreground)
            Text(signedPercent(value), style = MaterialTheme.typography.labelMedium.tabular(), color = PuckColors.Secondary)
        }
        if (preview) {
            Slider(
                value = value.toFloat(), onValueChange = { controller.simulate(axis, it.toDouble()) }, valueRange = -1f..1f,
                colors = SliderDefaults.colors(thumbColor = PuckColors.Warm, activeTrackColor = PuckColors.Track, inactiveTrackColor = PuckColors.Track),
                modifier = Modifier.fillMaxWidth().height(28.dp).testTag("preview-axis-${axis.name}")
                    .semantics { contentDescription = "Simulate ${axis.label}" },
            )
        } else {
            val active = users.isNotEmpty()
            Canvas(
                Modifier.fillMaxWidth().height(8.dp).semantics {
                    contentDescription = axis.label
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat().coerceIn(-1f, 1f), -1f..1f)
                },
            ) {
                drawRoundRect(PuckColors.Track, cornerRadius = CornerRadius(size.height / 2))
                val c = size.width / 2
                val end = c + (value.coerceIn(-1.0, 1.0) * c).toFloat()
                drawRoundRect(if (active) PuckColors.Warm else PuckColors.Secondary, Offset(minOf(c, end), 0f), Size(abs(end - c), size.height),
                    CornerRadius(size.height / 2))
                drawLine(PuckColors.Control, Offset(c, 0f), Offset(c, size.height), 1.dp.toPx())
            }
        }
        if (users.isNotEmpty()) Text("→ ${users.joinToString { it.output.label }}", style = MaterialTheme.typography.labelSmall, color = PuckColors.Secondary)
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

@Composable
private fun ButtonsScreen(state: AppState, controller: AppController, compact: Boolean) {
    val p = state.profile
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePad(compact)), verticalArrangement = Arrangement.spacedBy(Space.l)) {
        PageHeader("Buttons", "Choose what the two SpaceMouse buttons do in the “${p.name}” profile.")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            val first: @Composable (Modifier) -> Unit = { m -> ButtonCard("Button 1", "button1", p.button1, m) { controller.updateButtons(it, p.button2) } }
            val second: @Composable (Modifier) -> Unit = { m -> ButtonCard("Button 2", "button2", p.button2, m) { controller.updateButtons(p.button1, it) } }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) { first(Modifier.weight(1f)); second(Modifier.weight(1f)) }
            else Column(verticalArrangement = Arrangement.spacedBy(Space.l)) { first(Modifier.fillMaxWidth()); second(Modifier.fillMaxWidth()) }
        }
        Section("Ways to pause", Modifier.widthIn(max = 880.dp)) {
            val buttonPauses = listOf(p.button1, p.button2).any { it == ButtonAction.PAUSE_TOGGLE || it == ButtonAction.PAUSE_HOLD }
            Bullet("The Pause button at the top of this window and the tray menu.")
            Bullet("${state.settings.hotkey.label} from any app" + if (state.hotkeyReady) "." else " — not active right now; see Settings.")
            if (buttonPauses) Bullet("A SpaceMouse button set to Toggle pause or Pause while held.")
            Bullet("Clicks are only sent while output is active. A held click is released if Puck Mouse pauses or the device disconnects.")
            if (!buttonPauses && !state.hotkeyReady) {
                InlineNote("Neither button pauses and the keyboard shortcut isn't active. Consider setting one button to Toggle pause.")
            }
        }
    }
}

@Composable
private fun ButtonCard(title: String, tag: String, action: ButtonAction, modifier: Modifier, onChange: (ButtonAction) -> Unit) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(PuckColors.Surface).padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Picker(
            title = action.label, subtitle = null, tag = "$tag-action", description = "$title action",
            options = ButtonAction.entries, isSelected = { it == action }, optionTag = { "$tag-${it.name}" },
            optionTitle = { it.label }, optionSubtitle = { it.short() },
        ) { if (it != action) onChange(it) }
        Text(action.explain(), style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary)
    }
}

private fun ButtonAction.short() = when (this) {
    ButtonAction.LEFT_CLICK, ButtonAction.RIGHT_CLICK, ButtonAction.MIDDLE_CLICK -> "Click; hold to drag"
    ButtonAction.BACK -> "Browser and Explorer back"
    ButtonAction.FORWARD -> "Browser and Explorer forward"
    ButtonAction.PAUSE_TOGGLE -> "Press to pause, press again to resume"
    ButtonAction.PAUSE_HOLD -> "Clutch: paused only while held"
    ButtonAction.NONE -> "Ignore this button"
}

private fun ButtonAction.explain() = when (this) {
    ButtonAction.LEFT_CLICK -> "Press to click. Keep it held while moving the cap to drag."
    ButtonAction.RIGHT_CLICK -> "Press to right-click, for example to open context menus. Keep it held to right-drag."
    ButtonAction.MIDDLE_CLICK -> "Press to middle-click: opens links in a new tab, or pans in many design apps."
    ButtonAction.BACK -> "Works like the Back side button on a mouse — goes back in browsers and File Explorer."
    ButtonAction.FORWARD -> "Works like the Forward side button on a mouse."
    ButtonAction.PAUSE_TOGGLE -> "Press once to stop all pointer and scroll output; press again to resume. Movement restarts only after the cap rests at center."
    ButtonAction.PAUSE_HOLD -> "Works like a clutch: output stops while you hold the button so you can reposition your hand. Let go, let the cap settle at center, and it continues."
    ButtonAction.NONE -> "The button is ignored."
}

// ---------------------------------------------------------------------------------------------
// Profiles
// ---------------------------------------------------------------------------------------------

private const val MAX_PROFILES = 32

@Composable
private fun ProfilesScreen(state: AppState, controller: AppController, ask: (DialogRequest) -> Unit, compact: Boolean) {
    val profiles = state.settings.profiles
    val current = state.profile
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePad(compact)), verticalArrangement = Arrangement.spacedBy(Space.l)) {
        PageHeader("Profiles", "A profile holds four movement mappings and two button actions. Switching is manual — here or from the header.")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val list: @Composable (Modifier) -> Unit = { m ->
                Column(m.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("${profiles.size} of $MAX_PROFILES profiles")
                    profiles.forEachIndexed { i, p ->
                        val selected = p.id == current.id
                        Row(
                            Modifier.fillMaxWidth().focusRing(8.dp).clip(MaterialTheme.shapes.medium)
                                .background(if (selected) PuckColors.Selected else PuckColors.Surface)
                                .border(1.dp, if (selected) PuckColors.Control else Color.Transparent, MaterialTheme.shapes.medium)
                                .selectable(selected, role = Role.RadioButton) { if (!selected) controller.selectProfile(p.id) }
                                .testTag("profile-row-$i")
                                .padding(horizontal = Space.m, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
                        ) {
                            RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = PuckColors.Warm, unselectedColor = PuckColors.Control))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.oneLine(), style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            val detail: @Composable (Modifier) -> Unit = { m -> ProfileDetail(current, profiles.size, controller, ask, m) }
            if (maxWidth >= 720.dp) Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) { list(Modifier.width(300.dp)); detail(Modifier.weight(1f)) }
            else Column(verticalArrangement = Arrangement.spacedBy(Space.l)) { list(Modifier.fillMaxWidth()); detail(Modifier.fillMaxWidth()) }
        }
        Section("Import and export", Modifier.widthIn(max = 880.dp)) {
            Text("Export saves every profile and your pause shortcut to a .json file. Import adds the profiles from a file alongside yours; nothing is replaced.",
                style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary)
            Wrap {
                OutlinedButton(onClick = { chooseFile("Import profiles", FileDialog.LOAD)?.let { controller.importProfiles(it) } },
                    enabled = profiles.size < MAX_PROFILES, shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("profile-import")) { Text("Import…") }
                OutlinedButton(onClick = { chooseFile("Export profiles", FileDialog.SAVE, "puck-mouse-profiles.json")?.let { controller.exportProfiles(it) } },
                    shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("profile-export")) { Text("Export all…") }
            }
            if (profiles.size >= MAX_PROFILES) Text("Profile limit reached. Delete a profile to import more.", style = MaterialTheme.typography.bodySmall, color = PuckColors.Paused)
        }
    }
}

@Composable
private fun ProfileDetail(p: Profile, count: Int, controller: AppController, ask: (DialogRequest) -> Unit, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(PuckColors.Surface).padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        SectionLabel("In use")
        Text(p.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("profile-name").semantics { heading() })
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Output.entries.forEach { o ->
                val m = p.mappings.firstOrNull { it.output == o } ?: return@forEach
                SummaryRow(o.label, if (m.enabled) "${m.axis.label} · ${topSpeedText(m)}" else "Off")
            }
            SummaryRow("Button 1", p.button1.label)
            SummaryRow("Button 2", p.button2.label)
        }
        HorizontalDivider(color = PuckColors.Line)
        Wrap {
            OutlinedButton(onClick = { ask(NameRequest("Rename profile", p.name, "Rename") { controller.renameProfile(it) }) },
                shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("profile-rename")) { Text("Rename…") }
            OutlinedButton(onClick = { ask(NameRequest("Duplicate profile", "${p.name} copy".take(60), "Duplicate") { controller.duplicateProfile(it) }) },
                enabled = count < MAX_PROFILES, shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("profile-duplicate")) { Text("Duplicate…") }
            OutlinedButton(onClick = {
                ask(ConfirmRequest("Reset “${p.name}” to defaults?",
                    "This replaces all four movement mappings and both button actions with the defaults. The profile name stays the same.", "Reset") { controller.resetProfile() })
            }, shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("profile-reset")) { Text("Reset to defaults…") }
            if (count > 1) {
                TextButton(onClick = {
                    ask(ConfirmRequest("Delete “${p.name}”?", "Its mappings and button actions will be removed. This can't be undone.", "Delete") { controller.deleteProfile() })
                }, colors = ButtonDefaults.textButtonColors(contentColor = PuckColors.Error), modifier = Modifier.testTag("profile-delete")) { Text("Delete…") }
            }
        }
        if (count <= 1) Text("This is your only profile, so it can't be deleted.", style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
        if (count >= MAX_PROFILES) Text("Profile limit reached. Delete a profile to duplicate this one.", style = MaterialTheme.typography.bodySmall, color = PuckColors.Paused)
    }
}

private fun Profile.oneLine(): String {
    val pointer = mappings.firstOrNull { it.output == Output.POINTER_X }?.takeIf { it.enabled }?.axis?.label
    val scroll = mappings.firstOrNull { it.output == Output.SCROLL_Y }?.takeIf { it.enabled }?.axis?.label
    return listOfNotNull(pointer?.let { "Pointer: $it" }, scroll?.let { "Scroll: $it" }).joinToString(" · ").ifEmpty { "Pointer and scroll off" }
}

private fun chooseFile(title: String, mode: Int, suggested: String? = null): FilePath? {
    val dialog = FileDialog(null as Frame?, title, mode)
    try {
        dialog.file = suggested ?: "*.json"
        dialog.isVisible = true
        val file = dialog.file ?: return null
        val dir = dialog.directory ?: return null
        val name = if (mode == FileDialog.SAVE && !file.endsWith(".json", ignoreCase = true)) "$file.json" else file
        return FilePath.of(dir, name)
    } finally { dialog.dispose() }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

/** Pause first: it works alone and rarely clashes with typing. Every modifier is explicit so alternatives don't follow Hotkey defaults. */
private val hotkeyPresets = listOf(
    Hotkey(0x13, ctrl = false, alt = false, shift = false),
    Hotkey(0x50, ctrl = true, alt = true, shift = false),
    Hotkey(0x78, ctrl = true, alt = false, shift = true),
    Hotkey(0x20, ctrl = true, alt = true, shift = false),
    Hotkey(0x91, ctrl = false, alt = false, shift = false),
)
private val recommendedHotkey = hotkeyPresets.first()

@Composable
private fun SettingsScreen(state: AppState, controller: AppController, compact: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePad(compact)), verticalArrangement = Arrangement.spacedBy(Space.l)) {
        PageHeader("Settings", "Applies to every profile.")
        val sectionWidth = Modifier.widthIn(max = 880.dp).fillMaxWidth()
        HotkeySection(state, controller, sectionWidth)
        Section("Window", sectionWidth) {
            ToggleRow("Keep running in the notification area", "Closing the window hides it to the tray so the pause shortcut keeps working. Quit from the tray icon.",
                state.settings.minimizeToTray, "tray-toggle") { controller.setMinimizeToTray(it) }
        }
        Section("Safety", sectionWidth) {
            Bullet("Always starts paused. Nothing moves until you choose Resume.")
            Bullet("After a pause, disconnect or settings change, output waits until the cap is released to center.")
            Bullet("Held clicks are released on pause, disconnect or mapping changes.")
            Bullet("Never starts with Windows on its own, and has no account, telemetry or network features.")
        }
        Section("Supported hardware", sectionWidth) {
            Text("The 3Dconnexion SpaceMouse that identifies over Bluetooth as 256f:c63a, using its combined motion report. Other models and connection types aren't supported yet.",
                style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary)
        }
        Section("Storage", sectionWidth) {
            Text("Settings save automatically on this computer as you change them.", style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary)
            SaveIndicator(state)
        }
        Section("About", sectionWidth) {
            SelectionContainer {
                Text("Puck Mouse ${AppVersion.value}", style = MaterialTheme.typography.bodyMedium,
                    color = PuckColors.Secondary, modifier = Modifier.testTag("app-version"))
            }
        }
    }
}

@Composable
private fun HotkeySection(state: AppState, controller: AppController, modifier: Modifier) {
    val hotkey = state.settings.hotkey
    var recording by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    Section("Pause shortcut", modifier) {
        Text("Pauses or resumes Puck Mouse from any app, even while this window is hidden.", style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary)
        Wrap {
            Keycaps(hotkey)
            Row(Modifier.heightIn(min = 32.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag("hotkey-status"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                StatusDot(if (state.hotkeyReady) Tone.Active else Tone.Attention)
                Text(if (state.hotkeyReady) "Active in all apps" else "Not active", style = MaterialTheme.typography.labelLarge,
                    color = if (state.hotkeyReady) PuckColors.Active else PuckColors.Paused)
            }
        }
        if (!state.hotkeyReady) {
            Text("Windows hasn't accepted this shortcut — another app may already use it. Choose a different one below.",
                style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
        }
        if (recording) {
            ShortcutCapture(
                onCapture = { problem = null; recording = false; if (it != hotkey) controller.setHotkey(it) },
                onProblem = { problem = it },
                onCancel = { recording = false },
            )
        } else {
            OutlinedButton(onClick = { problem = null; recording = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("hotkey-record")) {
                Text("Record new shortcut…")
            }
        }
        problem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = PuckColors.Error, modifier = Modifier.testTag("hotkey-problem")) }
        SectionLabel("Or pick one")
        Wrap {
            hotkeyPresets.forEachIndexed { i, preset ->
                FilterChip(
                    selected = preset == hotkey, onClick = { problem = null; if (preset != hotkey) controller.setHotkey(preset) },
                    label = { Text(if (preset == recommendedHotkey) "${preset.label} · Recommended" else preset.label) },
                    modifier = Modifier.testTag("hotkey-preset-$i"),
                    leadingIcon = if (preset == hotkey) ({ Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp)) }) else null,
                )
            }
        }
        Text("Pause is recommended: it works on its own and rarely gets in the way of typing. Scroll Lock also works alone. " +
            "Otherwise use Ctrl, Alt or Shift with a letter, number or function key. F12 is reserved by Windows.",
            style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
    }
}

@Composable
private fun Keycaps(hotkey: Hotkey) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "Current shortcut ${hotkey.label}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        hotkey.label.split(" + ").forEach { part ->
            Text(part, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.border(1.dp, PuckColors.Control, MaterialTheme.shapes.extraSmall).background(PuckColors.Background, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

private val modifierKeyCodes = setOf(AwtKey.VK_SHIFT, AwtKey.VK_CONTROL, AwtKey.VK_ALT, AwtKey.VK_ALT_GRAPH, AwtKey.VK_META, AwtKey.VK_WINDOWS)

/** AWT and Win32 share codes for letters, digits, F1–F12, Space, Pause and Scroll Lock; F13–F24 differ. */
private fun awtToWindowsVk(code: Int): Int? = when (code) {
    in 0x30..0x39, in 0x41..0x5A -> code
    in AwtKey.VK_F1..AwtKey.VK_F12 -> 0x70 + (code - AwtKey.VK_F1)
    in AwtKey.VK_F13..AwtKey.VK_F24 -> 0x7C + (code - AwtKey.VK_F13)
    AwtKey.VK_SPACE -> 0x20
    AwtKey.VK_PAUSE -> 0x13
    AwtKey.VK_SCROLL_LOCK -> 0x91
    else -> null
}

private fun hotkeyProblem(vk: Int?, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean): String? = when {
    meta -> "Windows key combinations are reserved by Windows. Try Ctrl, Alt or Shift instead."
    vk == null -> "That key can't be used. Choose a letter, number, function key, Space, Pause or Scroll Lock."
    vk == 0x7B -> "F12 is reserved by Windows. Choose another key."
    !(ctrl || alt || shift) && vk != 0x13 && vk != 0x91 -> "Add Ctrl, Alt or Shift so the shortcut doesn't get in the way of typing."
    else -> null
}

@Composable
private fun ShortcutCapture(onCapture: (Hotkey) -> Unit, onProblem: (String) -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    var held by remember { mutableStateOf("") }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun modifiers(e: KeyEvent) = listOfNotNull(if (e.isCtrlPressed) "Ctrl" else null, if (e.isAltPressed) "Alt" else null, if (e.isShiftPressed) "Shift" else null).joinToString(" + ")
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).border(2.dp, PuckColors.Warm, MaterialTheme.shapes.small).background(PuckColors.Raised)
            .focusRequester(focus)
            .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) onCancel() }
            .onPreviewKeyEvent { e ->
                val code = (e.nativeKeyEvent as? AwtKey)?.keyCode ?: e.key.nativeKeyCode
                if (e.type == KeyEventType.KeyUp) { held = modifiers(e); return@onPreviewKeyEvent true }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                val anyModifier = e.isCtrlPressed || e.isAltPressed || e.isShiftPressed || e.isMetaPressed
                when {
                    code in modifierKeyCodes -> held = modifiers(e)
                    code == AwtKey.VK_ESCAPE && !anyModifier -> onCancel()
                    code == AwtKey.VK_TAB && !anyModifier -> { onCancel(); return@onPreviewKeyEvent false }
                    else -> {
                        val vk = awtToWindowsVk(code)
                        val problem = hotkeyProblem(vk, e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.isMetaPressed)
                        if (problem != null) onProblem(problem) else onCapture(Hotkey(vk!!, e.isCtrlPressed, e.isAltPressed, e.isShiftPressed))
                    }
                }
                true
            }
            .focusable()
            .testTag("hotkey-capture")
            .semantics { contentDescription = "Recording shortcut. Press the new key combination, or Escape to cancel." }
            .padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(if (held.isEmpty()) "Press the new shortcut…" else "$held + …", style = MaterialTheme.typography.titleMedium)
        Text("Press Pause or Scroll Lock, or hold Ctrl, Alt or Shift and press a key. Esc cancels.", style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
    }
}

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

private fun Modifier.escapeDismisses(onDismiss: () -> Unit) = onPreviewKeyEvent {
    if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) { onDismiss(); true } else false
}

@Composable
private fun DialogHost(request: DialogRequest, onClose: () -> Unit) {
    when (request) {
        is ConfirmRequest -> AlertDialog(
            onDismissRequest = onClose,
            modifier = Modifier.escapeDismisses(onClose).widthIn(max = 480.dp).testTag("confirm-dialog"),
            shape = MaterialTheme.shapes.medium,
            containerColor = PuckColors.Surface,
            title = { Text(request.title, style = MaterialTheme.typography.titleMedium) },
            text = { Text(request.body, style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary) },
            confirmButton = {
                Button(onClick = { onClose(); request.onConfirm() }, shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("profile-confirm")) {
                    Text(request.action)
                }
            },
            dismissButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("dialog-cancel")) { Text("Cancel") } },
        )
        is NameRequest -> NameDialog(request, onClose)
    }
}

@Composable
private fun NameDialog(request: NameRequest, onClose: () -> Unit) {
    var field by remember(request) { mutableStateOf(TextFieldValue(request.initial, TextRange(0, request.initial.length))) }
    val focus = remember { FocusRequester() }
    // The dialog layer attaches a frame later; focusing earlier is silently ignored.
    LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
    val name = field.text.trim()
    val problem = when {
        name.isEmpty() -> "Enter a name"
        name.length > 60 -> "Use 60 characters or fewer"
        name.any { it.isISOControl() } -> "Remove line breaks and control characters"
        else -> null
    }
    val submit = { if (problem == null) { onClose(); request.onConfirm(name) } }
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.escapeDismisses(onClose).widthIn(max = 480.dp).testTag("name-dialog"),
        shape = MaterialTheme.shapes.medium,
        containerColor = PuckColors.Surface,
        title = { Text(request.title, style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = field, onValueChange = { field = it }, singleLine = true,
                label = { Text("Profile name") },
                isError = problem != null && field.text.isNotEmpty(),
                supportingText = { Text(if (problem != null && field.text.isNotEmpty()) problem else "${name.length} / 60") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("profile-name-field").onPreviewKeyEvent {
                    if ((it.key == Key.Enter || it.key == Key.NumPadEnter) && it.type == KeyEventType.KeyDown) { submit(); true } else false
                },
            )
        },
        confirmButton = {
            Button(onClick = submit, enabled = problem == null, shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("profile-confirm")) {
                Text(request.action)
            }
        },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("dialog-cancel")) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

private fun pagePad(compact: Boolean): Dp = if (compact) Space.l else Space.xl

/** Keyboard focus outline drawn just outside the control; place it before clip/background. */
@Composable
private fun Modifier.focusRing(corner: Dp = 6.dp): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.isFocused }.drawWithContent {
        drawContent()
        if (focused) {
            val inset = 3.dp.toPx()
            drawRoundRect(PuckColors.Warm, Offset(-inset, -inset), Size(size.width + 2 * inset, size.height + 2 * inset),
                CornerRadius(corner.toPx() + inset), style = Stroke(2.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Wrap(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) { content() }
}

@Composable
private fun PageHeader(title: String, subtitle: String, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary)
        }
        actions()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = PuckColors.Secondary)
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(PuckColors.Surface).padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = PuckColors.Secondary, modifier = Modifier.width(160.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InlineNote(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(PuckColors.PausedContainer).padding(Space.m).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Icon(Icons.Outlined.Info, null, tint = PuckColors.Paused, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun ToggleRow(title: String, caption: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusRing().clip(MaterialTheme.shapes.small)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .testTag(tag)
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
        }
        Switch(checked, onCheckedChange = null, colors = puckSwitchColors())
    }
}

@Composable
private fun LabeledSlider(label: String, value: String, tag: String, caption: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge.tabular(), color = PuckColors.Foreground)
        }
        Slider(
            value = current, onValueChange = onChange, valueRange = range, colors = puckSliderColors(),
            modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = label; stateDescription = value },
        )
        Text(caption, style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary)
    }
}

@Composable
private fun CheckSlot(selected: Boolean) {
    if (selected) Icon(Icons.Outlined.Check, null, modifier = Modifier.size(20.dp)) else Spacer(Modifier.size(20.dp))
}

@Composable
private fun <T> Picker(
    title: String, subtitle: String?, tag: String, description: String,
    options: List<T>, isSelected: (T) -> Boolean, optionTag: (T) -> String,
    optionTitle: (T) -> String, optionSubtitle: (T) -> String?, onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().focusRing().clip(MaterialTheme.shapes.small).border(1.dp, PuckColors.Control, MaterialTheme.shapes.small)
                .clickable(onClickLabel = "Change", role = Role.DropdownList) { open = true }
                .testTag(tag)
                .semantics(mergeDescendants = true) { contentDescription = "$description: $title" }
                .padding(horizontal = Space.m, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary) }
            }
            Icon(Icons.Outlined.ArrowDropDown, null, tint = PuckColors.Secondary)
        }
        DropdownMenu(open, { open = false }, containerColor = PuckColors.Raised) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column(Modifier.widthIn(min = 260.dp, max = 380.dp).padding(vertical = 4.dp)) {
                            Text(optionTitle(option), style = MaterialTheme.typography.bodyMedium)
                            optionSubtitle(option)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PuckColors.Secondary) }
                        }
                    },
                    onClick = { open = false; onPick(option) },
                    leadingIcon = { CheckSlot(isSelected(option)) },
                    modifier = Modifier.testTag(optionTag(option)),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Copy and formatting
// ---------------------------------------------------------------------------------------------

private val Output.isPointer get() = this == Output.POINTER_X || this == Output.POINTER_Y

private fun Output.effectNoun() = if (isPointer) "the pointer" else "the page"

private fun speedRange(o: Output) = if (o.isPointer) 50f..20000f else 1f..60f

private fun roundSpeed(o: Output, v: Double) = if (o.isPointer) (v / 10).roundToInt() * 10.0 else (v * 2).roundToInt() / 2.0

private fun trimNumber(v: Double) = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else "%.1f".format(v)

private fun speedText(o: Output, speed: Double) = if (o.isPointer) "${speed.roundToInt()} ${o.unit}" else "${trimNumber(speed)} ${o.unit}"

/** Top speed, plus where it is reached when that's before a full push. */
private fun topSpeedText(m: Mapping) = speedText(m.output, m.speed) + if (m.fullSpeedAt < 1) " at ${percent(m.fullSpeedAt)}" else ""

private fun rateText(o: Output, rate: Double): String {
    val number = if (o.isPointer) "%+d".format(rate.roundToInt()) else "%+.1f".format(rate)
    return if (number.trimStart('+', '-').trimEnd('0', '.', ',').isEmpty()) "0 ${o.unit}" else "$number ${o.unit}"
}

private fun percent(v: Double) = "${(v * 100).roundToInt()}%"

private fun signedPercent(v: Double): String {
    val p = (v * 100).roundToInt()
    return if (p == 0) "0%" else "%+d%%".format(p)
}

private fun curveName(c: Double) = when {
    c < 0.95 -> "quick start"
    c <= 1.05 -> "straight"
    c <= 3.5 -> "gentle start"
    else -> "very gentle start"
}

private fun androidx.compose.ui.text.TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private fun Mapping.sentence(): String {
    val movement = when (axis) {
        Axis.SLIDE_X -> "Slide the cap sideways"
        Axis.SLIDE_Y -> "Slide the cap forward or back"
        Axis.LIFT -> "Press or lift the cap"
        Axis.TILT_X -> "Tilt the cap forward or back"
        Axis.TILT_Y -> "Tilt the cap sideways"
        Axis.TWIST -> "Twist the cap"
    }
    val effect = when (output) {
        Output.POINTER_X -> "move the pointer left and right"
        Output.POINTER_Y -> "move the pointer up and down"
        Output.SCROLL_Y -> "scroll up and down"
        Output.SCROLL_X -> "scroll sideways"
    }
    if (!enabled) return "Off. ${axis.label} doesn't affect ${output.label.lowercase()} in this profile."
    val reach = if (fullSpeedAt < 1) " from a ${percent(fullSpeedAt)} push" else ""
    return "$movement to $effect, up to ${speedText(output, speed)}$reach" + (if (inverted) ", direction reversed." else ".")
}
