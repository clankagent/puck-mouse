package dev.puckmouse

import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Serializable enum class Axis(val label: String, val hint: String, val wire: String) {
    SLIDE_X("Slide sideways", "Move the cap left and right", "x"),
    SLIDE_Y("Slide forward / back", "Move the cap away from you and towards you", "y"),
    LIFT("Press / lift", "Push the cap down or lift it up", "z"),
    TILT_X("Tilt forward / back", "Tip the cap away from you and towards you", "rx"),
    TILT_Y("Tilt sideways", "Tip the cap left and right", "ry"),
    TWIST("Twist", "Turn the cap clockwise and counterclockwise", "rz")
}
@Serializable enum class Output(val label: String, val unit: String) {
    POINTER_X("Pointer · horizontal", "px/s"), POINTER_Y("Pointer · vertical", "px/s"),
    SCROLL_Y("Scroll · vertical", "notches/s"), SCROLL_X("Scroll · horizontal", "notches/s")
}
@Serializable data class Mapping(
    val output: Output, val axis: Axis, val enabled: Boolean = true,
    val speed: Double = 900.0, val deadzone: Double = .08,
    val curve: Double = 1.4, val responseMs: Double = 25.0, val inverted: Boolean = false
)
@Serializable enum class ButtonAction(val label: String) {
    LEFT_CLICK("Left click"), RIGHT_CLICK("Right click"), MIDDLE_CLICK("Middle click"),
    BACK("Back"), FORWARD("Forward"), PAUSE_TOGGLE("Toggle pause"), PAUSE_HOLD("Pause while held"), NONE("Do nothing")
}
@Serializable data class Profile(val id: String = UUID.randomUUID().toString(), val name: String = "Everyday",
    val mappings: List<Mapping> = defaultMappings(),
    val button1: ButtonAction = ButtonAction.LEFT_CLICK, val button2: ButtonAction = ButtonAction.PAUSE_TOGGLE)
fun defaultMappings() = listOf(
    Mapping(Output.POINTER_X, Axis.SLIDE_X), Mapping(Output.POINTER_Y, Axis.SLIDE_Y, inverted = true),
    Mapping(Output.SCROLL_Y, Axis.TWIST, speed = 12.0, curve = 1.25),
    Mapping(Output.SCROLL_X, Axis.TILT_Y, enabled = false, speed = 10.0)
)
@Serializable data class Hotkey(val key: Int = 0x50, val ctrl: Boolean = true, val alt: Boolean = true, val shift: Boolean = false) {
    val label: String get() = listOfNotNull(if(ctrl) "Ctrl" else null, if(alt) "Alt" else null, if(shift) "Shift" else null,
        when(key) { 0x20 -> "Space"; 0x13 -> "Pause"; 0x91 -> "Scroll Lock"; in 0x70..0x87 -> "F${key-0x6f}"; else -> key.toChar().toString() }).joinToString(" + ")
    val modifiers: Int get() = (if(ctrl) 2 else 0) or (if(alt) 1 else 0) or (if(shift) 4 else 0) or 0x4000
}
@Serializable data class Settings(val schema: Int = 1, val profiles: List<Profile> = listOf(Profile()),
    val selectedId: String = profiles.first().id, val hotkey: Hotkey = Hotkey(), val minimizeToTray: Boolean = true)
data class Axes(val x: Double=0.0, val y: Double=0.0, val z: Double=0.0, val rx: Double=0.0, val ry: Double=0.0, val rz: Double=0.0) {
    fun values() = listOf(x,y,z,rx,ry,rz)
    operator fun get(axis: Axis) = values()[axis.ordinal]
    fun with(axis: Axis, value: Double): Axes { val v=values().toMutableList(); v[axis.ordinal]=value.coerceIn(-1.0,1.0); return Axes(v[0],v[1],v[2],v[3],v[4],v[5]) }
    fun neutral() = values().all { kotlin.math.abs(it) <= .03 }
}
data class AppState(val settings: Settings = Settings(), val paused: Boolean = true, val armed: Boolean = false,
    val connected: Boolean = false, val deviceName: String = "No SpaceMouse connected", val axes: Axes = Axes(),
    val outputs: Map<Output, Double> = emptyMap(), val preview: Boolean = false, val error: String? = null,
    val hotkeyReady: Boolean = false, val message: String? = null) {
    val profile: Profile get() = settings.profiles.first { it.id == settings.selectedId }
    val status: String get() = when { preview -> "Preview only"; paused -> "Paused"; !connected -> "Waiting for device"; !armed -> "Release the cap to resume"; else -> "Active" }
}

/** UI contract. Native implementation queues commands on its single input thread. */
class AppController(private val store: SettingsStore = SettingsStore.default(), private val native: Boolean = true, allowDesktopOutput: Boolean = true) : AutoCloseable {
    private val mutable = MutableStateFlow(AppState(settings = store.load()))
    val state: StateFlow<AppState> = mutable
    private var host: WindowsHost? = null
    private val writer=Executors.newSingleThreadScheduledExecutor {r->Thread(r,"Puck Mouse settings").apply{isDaemon=true}}
    private var pendingSave: ScheduledFuture<*>?=null
    private val dirty=AtomicBoolean(false)
    init { mutable.update { it.copy(error=store.loadError) }; if(native) { host=WindowsHost({ mutable.value }, { transform -> mutable.update(transform) },allowDesktopOutput); host!!.start() } }
    private fun change(transform: (Settings)->Settings) {
        try { val settings=validateSettings(transform(mutable.value.settings))
            mutable.update { it.copy(settings=settings, message=null, error=null) }; host?.reconfigure()
            dirty.set(true);pendingSave?.cancel(false)
            pendingSave=writer.schedule({try{store.save(settings);if(mutable.value.settings==settings){dirty.set(false);mutable.update{it.copy(message="Saved")}}}catch(e:Exception){reportError(e.message?:"Could not save settings")}},200,TimeUnit.MILLISECONDS)
        } catch(e: Exception) { reportError(e.message ?: "Could not save settings") }
    }
    fun updateMapping(mapping: Mapping) = change { s -> s.copy(profiles=s.profiles.map { p -> if(p.id==s.selectedId) p.copy(mappings=p.mappings.map { if(it.output==mapping.output) mapping else it }) else p }) }
    fun updateButtons(first: ButtonAction, second: ButtonAction) = change { s -> s.copy(profiles=s.profiles.map { if(it.id==s.selectedId) it.copy(button1=first,button2=second) else it }) }
    fun selectProfile(id: String) = change { it.copy(selectedId=id) }
    fun duplicateProfile(name: String) = change { s -> val p=mutable.value.profile.copy(id=UUID.randomUUID().toString(),name=name.trim()); s.copy(profiles=s.profiles+p,selectedId=p.id) }
    fun renameProfile(name: String) = change { s -> s.copy(profiles=s.profiles.map { if(it.id==s.selectedId) it.copy(name=name.trim()) else it }) }
    fun deleteProfile() = change { s -> check(s.profiles.size>1) { "Keep at least one profile" }; val p=s.profiles.filter { it.id!=s.selectedId }; s.copy(profiles=p,selectedId=p.first().id) }
    fun resetProfile() = change { s -> s.copy(profiles=s.profiles.map { if(it.id==s.selectedId) it.copy(mappings=defaultMappings(),button1=ButtonAction.LEFT_CLICK,button2=ButtonAction.PAUSE_TOGGLE) else it }) }
    fun setHotkey(hotkey: Hotkey) = change { it.copy(hotkey=hotkey) }
    fun setMinimizeToTray(value: Boolean) = change { it.copy(minimizeToTray=value) }
    fun togglePause() { if(host!=null) host!!.togglePause() else mutable.value=mutable.value.copy(paused=!mutable.value.paused,armed=false) }
    fun setPreview(value: Boolean) { if(host!=null) host!!.setPreview(value) else mutable.value=mutable.value.copy(preview=value,paused=true,axes=Axes()) }
    fun simulate(axis: Axis, value: Double) { if(host!=null) host!!.simulate(axis,value) else mutable.value=mutable.value.copy(axes=mutable.value.axes.with(axis,value)) }
    fun centerPreview() { if(host!=null) host!!.centerPreview() else mutable.value=mutable.value.copy(axes=Axes()) }
    fun importProfiles(path: Path) { try { val incoming=store.read(path); change { s -> val p=incoming.profiles.map { it.copy(id=UUID.randomUUID().toString()) }; s.copy(profiles=s.profiles+p,selectedId=p.first().id) } } catch(e:Exception) {reportError(e.message?:"Could not import profiles")} }
    fun exportProfiles(path: Path) { try { store.write(path, mutable.value.settings); mutable.value=mutable.value.copy(message="Profiles exported",error=null) } catch(e:Exception){reportError(e.message?:"Could not export profiles")} }
    fun dismissMessage() { mutable.update { it.copy(message=null,error=null) } }
    fun reportError(message: String) { mutable.update { it.copy(error=message) } }
    override fun close() { host?.close();pendingSave?.cancel(false);writer.shutdown();writer.awaitTermination(2,TimeUnit.SECONDS)
        if(dirty.get())try{store.save(mutable.value.settings)}catch(e:Exception){reportError(e.message?:"Could not save settings")}
    }
}
