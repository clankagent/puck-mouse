package dev.puckmouse

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

fun validateSettings(s: Settings): Settings {
    require(s.schema==1) { "Unsupported settings version" }
    require(s.profiles.size in 1..32) { "Keep between 1 and 32 profiles" }
    require(s.profiles.map { it.id }.distinct().size==s.profiles.size) { "Duplicate profile IDs" }
    require(s.profiles.any { it.id==s.selectedId }) { "Selected profile is missing" }
    require(s.hotkey.key in 0x30..0x39 || s.hotkey.key in 0x41..0x5a || s.hotkey.key in 0x70..0x87 || s.hotkey.key in listOf(0x20,0x13,0x91)) { "Choose a letter, number, function key, Space, Pause or Scroll Lock" }
    require(s.hotkey.key!=0x7b) { "F12 is reserved by Windows" }
    require(s.hotkey.ctrl || s.hotkey.alt || s.hotkey.shift || s.hotkey.key in listOf(0x13,0x91)) { "Include Ctrl, Alt or Shift" }
    for(p in s.profiles) {
        require(p.name.isNotBlank() && p.name.length<=60 && p.name.none { it.isISOControl() }) { "Profile names need 1–60 readable characters" }
        require(p.id.length in 1..80) { "Invalid profile ID" }
        require(p.mappings.map{it.output}.toSet()==Output.entries.toSet() && p.mappings.size==4) { "Each output needs exactly one mapping" }
        for(m in p.mappings) {
            require(m.speed.isFinite() && m.speed in 0.1..if(m.output.name.startsWith("POINTER")) 4000.0 else 60.0) { "Speed is outside its supported range" }
            require(m.deadzone.isFinite() && m.deadzone in 0.0..0.5) { "Dead zone must be between 0 and 50%" }
            require(m.curve.isFinite() && m.curve in 0.5..3.0) { "Response curve must be between 0.5 and 3" }
            require(m.responseMs.isFinite() && m.responseMs in 0.0..150.0) { "Smoothing must be between 0 and 150 ms" }
        }
    }
    return s
}
class SettingsStore(private val path: Path?) {
    private val json=Json { prettyPrint=true }
    fun load(): Settings = if(path==null || !Files.exists(path)) Settings() else try {read(path)} catch(e:Exception){
        // Retain the invalid original and use defaults; expose the problem to the UI.
        loadError="Saved settings could not be loaded. The original file was preserved. ${e.message}"; Settings()
    }
    var loadError: String? = null; private set
    private var originalBackedUp=false
    fun read(file: Path): Settings { require(Files.size(file)<=65536) { "Settings file is larger than 64 KiB" }; return validateSettings(json.decodeFromString<Settings>(Files.readString(file))) }
    fun save(settings: Settings) { if(path!=null) {
        if(loadError!=null && !originalBackedUp && Files.exists(path)) {
            Files.copy(path,path.resolveSibling("settings.invalid-${System.currentTimeMillis()}.json"))
            originalBackedUp=true
        }
        write(path,settings)
    } }
    fun write(file: Path, settings: Settings) {
        val text=json.encodeToString(validateSettings(settings)); require(text.toByteArray().size<=65536) { "Settings exceed 64 KiB" }
        val destination=file.toAbsolutePath(); Files.createDirectories(destination.parent)
        val temp=Files.createTempFile(destination.parent,"puckmouse-",".tmp")
        try { Files.writeString(temp,text); try {Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
            catch(e: java.nio.file.AtomicMoveNotSupportedException) {Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING)}
        } finally {Files.deleteIfExists(temp)}
    }
    companion object {
        fun default() = SettingsStore(Path.of(System.getenv("APPDATA") ?: System.getProperty("user.home"),"PuckMouse","settings.json"))
    }
}
